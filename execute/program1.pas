PUSH D0
MOV SP D0
ADD SP #1 SP
MOV #1 0(D0)
WRT #"A = "
WRT 0(D0)
MOV D0 SP
POP D0
HLT