package klisp

class KLispException(message: String) : Exception(message) {
    constructor(message: String, symbol: String) : this("$message : \"$symbol\"")
}