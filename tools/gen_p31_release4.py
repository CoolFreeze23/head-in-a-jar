"""Lines for the 0.4.0 release's own fixes. Imported by gen_data.py."""


def run(EN, PT, write, shaped):
    # the Separation Chair could not seat the player (the seat could not be taken): never a silent click
    EN['refusal.headinajar.chair.no_seat'] = "The chair couldn't seat you. Step back and try again."
    PT['refusal.headinajar.chair.no_seat'] = 'A cadeira não conseguiu sentar você. Afaste-se e tente de novo.'
    # already riding one of the company's machines, seats or shells: get down first
    EN['refusal.headinajar.chair.riding'] = "You're riding %s. Get down, then take a seat."
    PT['refusal.headinajar.chair.riding'] = 'Você está montado em %s. Desça e depois sente-se.'
