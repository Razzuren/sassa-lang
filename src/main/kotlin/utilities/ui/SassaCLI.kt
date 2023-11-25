package utilities.ui

import utilities.parsers.*
import java.io.File

const val red = "\u001b[31m"
const val reset = "\u001b[0m"
const val debug = false

fun main(args: Array<String>) {
    when {
        args.isEmpty() -> {
            // If no arguments are provided, ask the user for input
            println("Choose an option:")
            println("1. Test a predefined string")
            println("2. Enter your own string input")
            println("3. Enter a file location to parse")
            print("Enter the option number: ")
            val option = readlnOrNull()

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
    val lexerResult = SassaLexer(debug).lexer(sourceCode)

    val exitCode = lexerResult["exitcode"] as Int
    val tokens = lexerResult["tokens"] as List<Token>

    println("Lexer finished with Exit Code: $exitCode")

    tokens.forEach {
        println(
            "Token Type: ${it.type::class.simpleName}, " +
                    "Text: '${it.text}', Line: ${it.line}, Column: ${it.column}"
        )
    }

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
    val parser = SassaParser(debug)
    val parserResult = parser.parse(tokens)

    // Display parsing result
    val parserExitCode = parserResult["exitcode"] as Int
    val parsedStatements = parserResult["parsed_statements"] as List<Statement>

    println("\nParser Exit Code: $parserExitCode")

    if (parserExitCode > 0) {
        parsedStatements.forEach { println(red + it.tokens[0].text + reset) }

    } else {
        println("Parsed Statements:")
        parsedStatements.forEach { i ->
            println("------------------")
            println(i.statementType)
            i.tokens.forEach { j ->
                println(
                    "Token Type: ${j.type::class.simpleName}, " +
                            "Text: '${j.text}', Line: ${j.line}, Column: ${j.column}"
                )
            }
            println("")

        }
    }
}

private fun testPredefinedString() {
    val sourceCode = """
        /* this is a comment */
        str test(str argument, num argument2){
            out(argument) /* this is a comment */
            out(argument2)
            return 'hello'
        }
        
        main {
            num x = 5
            if (x == 5) {
                num y = 7.2 % 2
            } else {
                loop ( num z = 0 .. 9) {
                    /*this is also a comment*/ str y = 'its dangerous'
                    test( y , z)
                }
            }
        }
    """
    parseAndDisplayTokens(sourceCode)
}

private fun parseUserStringInput() {
    print("Enter your own string input: ")
    val sourceCode = readlnOrNull()
    if (sourceCode != null) {
        parseAndDisplayTokens(sourceCode)
    }
}

private fun parseFileInput() {
    print("Enter the file location to parse: ")
    val filePath = readlnOrNull()
    if (File(filePath!!).isFile) {
        val sourceCode = File(filePath!!).readText()
        parseAndDisplayTokens(sourceCode)
    } else {
        println("File not found: $filePath")
    }
}