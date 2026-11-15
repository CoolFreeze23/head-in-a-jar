"""Lines for the 0.5.4 release's own fixes. Imported by gen_data.py, before the handbook generator (gen_p27_clarity),
whose text check reads the final lines."""


def run(EN, PT, write, shaped):
    # a bloodless tool on another bloodless kind: name the tool that creature takes (an argument: the tool's name)
    EN['message.headinajar.other_bloodless_tool'] = 'Wrong tool for this creature. Use the %s.'
    PT['message.headinajar.other_bloodless_tool'] = 'Ferramenta errada para esta criatura. Use a %s.'
    # the Hemo-Syringe on a mecha's arm, at a thick hide: the hand syringes' own line
    EN['refusal.headinajar.mecha.arm.hemo_hide'] = EN['message.headinajar.boss_hide']
    PT['refusal.headinajar.mecha.arm.hemo_hide'] = PT['message.headinajar.boss_hide']
    # the Hemo-Syringe on a bloodless creature: nothing to draw, so no jab; the line names the tool it takes
    EN['refusal.headinajar.mecha.arm.hemo_bloodless'] = EN['message.headinajar.other_bloodless_tool']
    PT['refusal.headinajar.mecha.arm.hemo_bloodless'] = PT['message.headinajar.other_bloodless_tool']
    # a Board Meeting's motion that the nays have, or a tie
    EN['array.headinajar.vote_lost'] = '[Board Meeting] Motion: %s. Not carried: %s for, %s against.'
    PT['array.headinajar.vote_lost'] = '[Reunião do Conselho] Moção: %s. Rejeitada: %s a favor, %s contra.'
