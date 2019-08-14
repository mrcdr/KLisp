package klisp

class KLispUtil {
    companion object {
        fun gcd(x_init: Long, y_init:Long): Long {
            var x = x_init
            var y = y_init

            if(y > x) {
                x = y.also{ y = x } // swap values
            }

            //　Euclidean algorithm
            while(y != 0L) {
                x = y.also{ y = x % y}
            }

            return x
        }
    }
}
