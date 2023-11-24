package utilities.ui

import utilities.parsers.SassaLexer
import utilities.parsers.SassaParser
import utilities.parsers.Token
import utilities.parsers.TokenType
import java.io.File

const val red = "\u001b[31m"
const val reset = "\u001b[0m"

fun main(args: Array<String>) {
    when {
        args.isEmpty() -> {
            // If no arguments are provided, ask the user for input
            println("Choose an option:")
            println("1. Test a predefined string")
            println("2. Enter your own string input")
            println("3. Enter a file location to parse")
            print("Enter the option number: ")
            val option = readLine()

            when (option) {
                "1" -> testPredefinedString()
                "2" -> parseUserStringInput()
                "3" -> parseFileInput()
                else -> println("Invalid option")
            }
        }
        args.size == 1 -> {
            // If one argument is provided, consider it a string input
            val sourceCode = args[0]
            parseAndDisplayTokens(sourceCode)
        }
        args.size == 2 && args[0] == "-f" -> {
            // If two arguments are provided with -f flag, consider the second argument as a file location
            val filePath = args[1]
            if (File(filePath).isFile) {
                val sourceCode = File(filePath).readText()
                parseAndDisplayTokens(sourceCode)
            } else {
                println("File not found: $filePath")
            }
        }
        else -> {
            println("Invalid usage. Use one of the following:")
            println("1. To test a predefined string: java -jar SassaCLI.jar")
            println("2. To enter your own string input: java -jar SassaCLI.jar 'your_code_here'")
            println("3. To enter a file location to parse: java -jar SassaCLI.jar -f 'file_path'")
        }
    }
}


private fun parseAndDisplayTokens(sourceCode: String) {
    val lexerResult = SassaLexer().lexer(sourceCode, false)

    val exitCode = lexerResult["exitcode"] as Int
    val tokens = lexerResult["tokens"] as List<Token>

    for (token in tokens) {
        println("Token Type: ${token.type::class.simpleName}, " +
                "Text: '${token.text}', Line: ${token.line}, Column: ${token.column}")
    }

    println("Exit Code: $exitCode")

    if (exitCode > 0) {
        println("Invalid Tokens:")
        val invalidTokens = tokens.filter { it.type == TokenType.Invalid }
        invalidTokens.forEach { invalidToken ->
            println(red + "On line: ${invalidToken.line}, " +
                    "column: ${invalidToken.column}, found invalid token:" +
                    " ${invalidToken.text}" + reset)
        }
    }

    // Parse the tokens
    val parser = SassaParser(true)
    val parserResult = parser.parse(tokens)

    // Display parsing result
    val parserExitCode = parserResult["exitcode"] as Int
    val parsedStatements = parserResult["parsed_statements"]

    println("\nParser Exit Code: $parserExitCode")
    println("Parsed Statements:$parsedStatements")
}

private fun testPredefinedString() {
    //we expect two errors, one lost ' and ?
    val sourceCode = """
        /* this is a comment */
        str test(str argument, num argument2){
            out(argument)
            out(argument2)
        }
        
        main {
            if (x == 5) {
                num y = 7.2 % 2 
            } else {
                loop ( num z = 0 .. 9) {
                    str y = 'its dangerous'
                    test( y , z)
                }
            }
        }
    """
    parseAndDisplayTokens(sourceCode)
}

private fun parseUserStringInput() {
    print("Enter your own string input: ")
    val sourceCode = readLine()
    if (sourceCode != null) {
        parseAndDisplayTokens(sourceCode)
    }
}

private fun parseFileInput() {
    print("Enter the file location to parse: ")
    val filePath = readLine()
    if (File(filePath).isFile) {
        val sourceCode = File(filePath).readText()
        parseAndDisplayTokens(sourceCode)
    } else {
        println("File not found: $filePath")
    }
}