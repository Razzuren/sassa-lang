package utilities.ui

import utilities.parsers.SassaLexer
import utilities.parsers.Token

fun main() {
    val sourceCode = """
        /* this is a comment */     
        if (x == 5) {
            num y = 7.2 + 2 ;
        } else {
            loop ( num z = 0 .. 9) {
                str y = 'its dangerous'
            }
        }
    """
    val result = SassaLexer().lexer(sourceCode)

    val exitCode = result["exitcode"] as Int
    val tokens = result["tokens"] as List<Token>

    for (token in tokens) {
        println("Token Type: ${token.type::class.simpleName}, " +
                "Text: '${token.text}', Line: ${token.line}, Column: ${token.column}")
    }

    println("Exit Code: $exitCode")
}