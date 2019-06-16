package klisp

abstract class KLispSexp {
    abstract fun eval(env: KLispEnv): KLispSexp
}

abstract class KLispAtom(open val appearance: String) : KLispSexp()

open class KLispSymbol(override val appearance: String) : KLispAtom(appearance) {
    companion object {
        val T = object : KLispSymbol("t") {
            override fun eval(env: KLispEnv): KLispSexp {
                return this
            }
        }
    }

    override fun eval(env: KLispEnv): KLispSexp {
        return env.apply(this)
    }

    override fun toString(): String = appearance

    override fun equals(other: Any?): Boolean {
        return other is KLispSymbol && (this.appearance == other.appearance)
    }

    override fun hashCode(): Int {
        return this.appearance.hashCode()
    }
}

abstract class KLispValue(appearance: String) : KLispAtom(appearance) {
    override fun eval(env: KLispEnv) = this
}

abstract class KLispNumber(appearance: String) : KLispValue(appearance)

class KLispDouble(val value: Double) : KLispNumber(value.toString()) {
    override fun toString(): String {
        return value.toString()
    }
}

class KLispString(private val str: String) : KLispValue(str) {
    override fun eval(env: KLispEnv): KLispString = this
    override fun toString(): String {
        return "\"$str\""
    }
}

open class KLispList private constructor(private val list: List<KLispSexp>) : KLispSexp(), Iterable<KLispSexp> {
    companion object {
        val NIL = object : KLispList(emptyList()) {
            override fun eval(env: KLispEnv) = this
            override fun car() = this
            override fun cdr() = this

            override fun toString() = "nil"
        }

        fun createList(list: List<KLispSexp>): KLispList {
            return if(list.isEmpty()) {
                NIL
            } else {
                KLispList(list)
            }
        }
    }

    override fun eval(env: KLispEnv): KLispSexp {
        return env.apply(this)
    }

    fun subList(start: Int) = createList(list.subList(start, list.size))

    open fun car() = list[0]
    open fun cdr() = subList(1)

    operator fun get(at: Int) = list[at]

    val size: Int
    get() {
        return list.size
    }

    override fun iterator() = list.iterator()

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("(")
        for(elem in list) {
            builder.append(elem)
            builder.append(" ")
        }
        builder.setLength(builder.length-1) // remove last space
        builder.append(")")

        return builder.toString()
    }
}

abstract class KLispLambda : KLispValue("lambda") {
    abstract operator fun invoke(args: KLispList): KLispSexp
}