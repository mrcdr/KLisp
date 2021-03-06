package klisp

class KLispReader(input: String) {
    companion object {
        val QUOTE = "quote"
        val QUASIQUOTE = "quasiquote"
        val UNQUOTE = "unquote"
        val UNQUOTE_SPLICE = "unquote-splice"


        val TOKEN_REGEX = Regex("""[\s]*(,@|[()'`,]|"(?:\\.|[^\\"])*"|;.*|[^\s()'`,";]*)""")
        // remove space; paren and reader macro |  string literal | comment | appearance
        val FRACTION_REGEX = Regex("""(^[+-]?[0-9]+(?:/[0-9]+)?$)""")
        val FLOAT_REGEX = Regex("""(^[+-]?[0-9]+\.?[0-9]*$)""")
        val STRING_REGEX = Regex(""""((?:\\.|[^\\"])*)"""")
        val SYMBOL_REGEX = Regex("""(^.*)""")

        fun tokenize(input: String): Sequence<String> {
            return TOKEN_REGEX.findAll(input)
                    .map{ it.groups[1]?.value as String }
                    .filter{ it != "" && !it.startsWith(";")}
        }
    }

    private val sequence = tokenize(input)
    private val tokens = sequence.iterator()
    private var current = advance()

    fun next(): String? {
        val result = current
        current = advance()
        return result
    }

    fun peek() = current

    private fun advance(): String? = if (tokens.hasNext()) tokens.next() else null
}

fun readForm(reader: KLispReader): KLispSexp {
    return when (reader.peek()) {
        null -> throw KLispException("readForm null")
        "("  -> readList(reader)
        ")"  -> throw KLispException("form expected, got ')'")
        else -> readAtom(reader)
    }
}

fun readAtom(reader: KLispReader): KLispSexp {
    val token = reader.next() ?: throw KLispException("readAtom null")

    return when {
        KLispReader.FRACTION_REGEX.matches(token) -> KLispFraction(token)
        KLispReader.FLOAT_REGEX.matches(token) -> KLispDouble(token.toDouble())
        KLispReader.STRING_REGEX.matches(token) -> KLispString(token.substring(1, token.lastIndex)) // remove double quotes
        token == "'" -> KLispCons.createList(listOf(KLispSymbol(KLispReader.QUOTE), readForm(reader)))
        token == "`" -> KLispCons.createList(listOf(KLispSymbol(KLispReader.QUASIQUOTE), readForm(reader)))
        token == "," -> KLispCons.createList(listOf(KLispSymbol(KLispReader.UNQUOTE), readForm(reader)))
        token == ",@" -> KLispCons.createList(listOf(KLispSymbol(KLispReader.UNQUOTE_SPLICE), readForm(reader)))
        KLispReader.SYMBOL_REGEX.matches(token) -> {
            when(token) {
                KLispSymbol.T.toString() -> KLispSymbol.T
                KLispList.NIL.toString() -> KLispList.NIL
                else -> KLispSymbol(token)
            }
        }
        else -> throw KLispException("Unknown atom : $token")
    }
}

fun readList(reader: KLispReader): KLispList {
    reader.next()

    val list = mutableListOf<KLispSexp>()

    do {
        val form = when (reader.peek()) {
            null -> throw KLispException("')' expected, got EOF")
            ")"  -> { reader.next(); null }
            else -> readForm(reader)
        }

        if (form != null) {
            list.add(form)
        }
    } while (form != null)

    return KLispCons.createList(list)
}