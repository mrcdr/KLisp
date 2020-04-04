# KLisp
Lisp interpreter written in Kotlin

## Usage
Just run following:

``` shell
make
kotlin klisp.jar  # or  java -jar klisp.jar
```

## Spec

- Lisp-1

### Functions

- eval
- apply
- cons
- list
- car
- cdr
- \+ \- \* /
- = (number comparison)
- len (length of list)
- atom?
- quit

### Special forms

- lambda
- define
- let
- if
- quote

### Data types

- symbol
- number (fraction / floating point)
- string
- list
- lambda

### Special symbols

- t (boolean true)
- nil (boolean false and empty list)

### Reader macros
- ' (quote)
- \` (quasiquote)
- , (unquote)
- ,@ (unquote-splice)
