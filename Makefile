KC  := kotlinc
SRC := $(wildcard src/klisp/*.kt)

klisp: $(SRC)
	kotlinc $(SRC) -include-runtime -d klisp.jar
