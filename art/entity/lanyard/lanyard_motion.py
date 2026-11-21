"""How the worn lanyard, the badge and a biped's arms move: the reference maths StaffLayer and HumanoidModelCarryMixin
follow (port these functions as they are; every time is in ticks and runs on client render time, tick + partialTick,
so nothing steps at 20 a second).

    python art/entity/lanyard/lanyard_motion.py        # prints the curves at quarter ticks (a check, no files)

1. Sway (always, per worker, client-side state that is never saved or synced): the hang (strands and badge) is a
   small pendulum. Two angles in degrees, pitch about the hang's x (forward and back) and roll about its z (side to
   side), each a damped spring chasing a target, integrated in steps of at most half a tick so any frame rate gives
   the same curve (style guide M1, M9):
       target_pitch = clamp(-SPEED_LEAN * forward_speed, -LEAN_MAX, LEAN_MAX)            # trails behind the walk
                      + STEP_BOB * walk_amount * sin(2 * 0.6662 * walk_pos)               # two nods a stride
       target_roll  = clamp(TURN_SWING * body_yaw_rate, -ROLL_MAX, ROLL_MAX)              # swings out on turns
       acc = K * (target - angle) - C * vel;  vel += acc * dt;  angle += vel * dt
   forward_speed: blocks a tick along the body's facing (from the position change); walk_amount / walk_pos: the
   entity's walkAnimation speed and position; body_yaw_rate: degrees a tick (yBodyRot change, wrapped). The fit's
   `hang.sway` (1 for every species now) scales both angles.
2. The hire drop (10 ticks from the WorkerLook "just hired" stamp): the whole lanyard (ring and hang) comes down from
   DROP_FROM px above (model space, before the bone chain) with easeInQuad over DROP_TICKS, lands on tick 8 (the
   "Lanyard fitted" sound is the server's, on that tick), and the badge takes a forward kick (KICK degrees a tick
   added to the sway's pitch velocity) that the spring settles in about 10 ticks.
3. The off-pop on Let go (from the stamp of the badge coming off):
   - bipeds (HumanoidModel) bring both hands to the chest to unpin it: ticks 0-6 ease in-out (sine) from the arms'
     own pose to UNPIN, hold to tick 8, ease back to their own pose by tick 14;
   - on tick 7 (UNCLIP; the server's "Lanyard comes off" sound) the lanyard comes off over the head: the ring rises
     (POP_UP px/tick against POP_G: 16 px in 8 ticks, clear of a humanoid head) and comes forward (POP_FWD px/tick)
     while the badge swings up and over on its hang (POP_SPIN degrees a tick), and it is not drawn after POP_TICKS
     (the item is in the employer's hands by then). The ring passes through the head's box on the way up: a 1 px
     cord inside a head for 4 ticks, as a real lanyard goes over one;
   - animals: no arm pose; wolves also play vanilla's shake (entity event 8), the others the mod's head shake.
4. Carrying (bipeds holding the crate): both arms forward, CARRY_ARMS (radians, ModelPart rotations, the right arm's
   zRot negative so both hands close on the crate's sides), set after the model's own setupAnim; the walk still
   swings the legs. Chase the pose in and out over CARRY_EASE ticks (1 - exp(-dt * k), k = 12) so picking up and
   setting down never snap.
"""
import math

# sway
K = 0.35          # spring, per tick squared
C = 0.55          # damping, per tick (slightly under critical: one small overshoot)
SPEED_LEAN = 60.0  # degrees per (block a tick)
LEAN_MAX = 14.0
STEP_BOB = 5.0
TURN_SWING = 1.2  # degrees of roll per degree a tick of turning
ROLL_MAX = 12.0
MAX_STEP = 0.5    # ticks

# hire drop
DROP_FROM = 10.0
DROP_TICKS = 8.0
KICK = 6.0

# off-pop
UNPIN = {'right_arm': (-1.2, -0.3, 0.0), 'left_arm': (-1.2, 0.3, 0.0)}
UNPIN_IN, UNPIN_HOLD, UNPIN_OUT = 6.0, 8.0, 14.0
UNCLIP = 7.0
POP_UP = 4.0
POP_G = 0.5
POP_FWD = 1.2
POP_SPIN = -30.0
POP_TICKS = 8.0

# carrying
CARRY_ARMS = {'right_arm': (-1.0, 0.0, -0.08), 'left_arm': (-1.0, 0.0, 0.08)}
CARRY_EASE_K = 12.0


def clamp(x, a, b):
    return max(a, min(b, x))


def ease_in_quad(u):
    return u * u


def ease_in_out_sine(u):
    return 0.5 - 0.5 * math.cos(math.pi * clamp(u, 0, 1))


class Sway:
    """One worker's pendulum. step(dt, forward_speed, walk_amount, walk_pos, yaw_rate) -> (pitch, roll) degrees."""

    def __init__(self):
        self.p = self.r = self.vp = self.vr = 0.0

    def kick(self, dv):
        self.vp += dv

    def step(self, dt, forward_speed, walk_amount, walk_pos, yaw_rate, scale=1.0):
        tp = clamp(-SPEED_LEAN * forward_speed, -LEAN_MAX, LEAN_MAX) + STEP_BOB * walk_amount * math.sin(
            2 * 0.6662 * walk_pos)
        tr = clamp(TURN_SWING * yaw_rate, -ROLL_MAX, ROLL_MAX)
        left = dt
        while left > 1e-9:
            h = min(MAX_STEP, left)
            ap = K * (tp - self.p) - C * self.vp
            ar = K * (tr - self.r) - C * self.vr
            self.vp += ap * h
            self.vr += ar * h
            self.p += self.vp * h
            self.r += self.vr * h
            left -= h
        return self.p * scale, self.r * scale


def hire_offset(t):
    """px the lanyard is lifted (model up) t ticks after the hire stamp; 0 from DROP_TICKS on."""
    if t >= DROP_TICKS:
        return 0.0
    return DROP_FROM * (1.0 - ease_in_quad(clamp(t, 0, DROP_TICKS) / DROP_TICKS))


def unpin_weight(t):
    """0..1: how far the arms are toward UNPIN, t ticks after the let-go stamp."""
    if t <= UNPIN_IN:
        return ease_in_out_sine(t / UNPIN_IN)
    if t <= UNPIN_HOLD:
        return 1.0
    return 1.0 - ease_in_out_sine((t - UNPIN_HOLD) / (UNPIN_OUT - UNPIN_HOLD))


def pop(t):
    """(visible, up px, forward px, spin degrees) of the lanyard t ticks after the let-go stamp."""
    if t < UNCLIP:
        return True, 0.0, 0.0, 0.0
    u = t - UNCLIP
    if u >= POP_TICKS:
        return False, 0.0, 0.0, 0.0
    return True, POP_UP * u - 0.5 * POP_G * u * u, POP_FWD * u, POP_SPIN * u


if __name__ == '__main__':
    print('hire drop (t, lift px):', [(t / 4, round(hire_offset(t / 4), 2)) for t in range(0, 44, 4)])
    print('unpin (t, weight):', [(t / 2, round(unpin_weight(t / 2), 2)) for t in range(0, 30, 2)])
    print('pop (t, up, fwd, spin):', [(t, *[round(v, 1) for v in pop(t)[1:]]) for t in range(6, 17)])
    s = Sway()
    s.kick(KICK)
    print('kick settle (t, pitch):', [(t, round(s.step(1, 0, 0, 0, 0)[0], 2)) for t in range(1, 16)])
    s = Sway()
    print('walking (t, pitch):', [(t, round(s.step(1, 0.1, 0.8, t * 0.4, 0)[0], 1)) for t in range(1, 21)])
