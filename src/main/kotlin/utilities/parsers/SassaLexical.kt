package utilities.parsers

import java.io.File
import java.util.regex.Pattern

// Define token types
sealed class TokenType {
    object Comment : TokenType()
    object Keyword : TokenType()
    object Type : TokenType()
    object Boolean : TokenType()
    object Identifier : TokenType()
    object NumericalOperator : TokenType()
    object LogicalOperator : TokenType()
    object String : TokenType()
    object Number : TokenType()
    object Equals : TokenType()
    object OpenBrace : TokenType()
    object CloseBrace : TokenType()
    object OpenParenthesis : TokenType()
    object CloseParenthesis : TokenType()
    object ForCondition : TokenType()
    object Invalid : TokenType()
}

// Define the utilities.parsers.Token class with line and column information
data class Token(val type: TokenType, val text: String, val line: Int, val column: Int)

//I know this code is ugly as hell, I will refactor in the near future
//TODO() Refactor lexemes to be interpreted by it own method
class SassaLexer {

    //Lexer function that takes a file as input and outputs a file with the tokens
    fun lexer(file : File): Map<String, Any>{
        file.outputStream().bufferedWriter().use { out ->
            return lexer(file.readText())
        }
    }

    // Lexer function that takes a string as input and outputs a map with the exit code and the tokens
    fun lexer(sourceCode: String): Map<String, Any> {
        val tokens = mutableListOf<Token>()
        var currentIndex = 0
        var exitCode = 0
        var currentLine = 1

        val lines = sourceCode.split("\n")

        for (line in lines) {
            while (currentIndex < line.length) {
                // Skip whitespace characters
                if (line[currentIndex].isWhitespace()) {
                    currentIndex++
                    continue
                }

                println(line.substring(currentIndex))

                // Try to match a comment
                val commentPattern = Pattern.compile("\\/\\*.*?\\*\\/")
                val commentMatcher = commentPattern.matcher(line.substring(currentIndex))
                if (commentMatcher.find()) {
                    val comment = commentMatcher.group()
                    tokens.add(Token(TokenType.Comment, comment, currentLine, currentIndex))
                    currentIndex += comment.length
                    continue
                }

                if (line[currentIndex].isLetter()){
                    // Try to match a keyword
                    val keywordPattern = Pattern.compile("^(main|if|else|loop|out|in|return)")
                    val keywordMatcher = keywordPattern.matcher(line.substring(currentIndex))
                    if (keywordMatcher.find()) {
                        val keyword = keywordMatcher.group()
                        tokens.add(Token(TokenType.Keyword, keyword, currentLine, currentIndex))
                        currentIndex += keyword.length
                        continue
                    }

                    // Try to match a type
                    val typePattern = Pattern.compile("^(any|str|num|bool)")
                    val typeMatcher = typePattern.matcher(line.substring(currentIndex))
                    if (typeMatcher.find()) {
                        val type = typeMatcher.group()
                        tokens.add(Token(TokenType.Type, type, currentLine, currentIndex))
                        currentIndex += type.length
                        continue
                    }

                    // Try to match a boolaen value
                    val boolPattern = Pattern.compile("^(true|false)")
                    val boolMatcher = boolPattern.matcher(line.substring(currentIndex))
                    if (boolMatcher.find()) {
                        val bool = boolMatcher.group()
                        tokens.add(Token(TokenType.Boolean, bool, currentLine, currentIndex))
                        currentIndex += bool.length
                        continue
                    }

                    // Try to match an identifier
                    val identifierPattern = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*")
                    val identifierMatcher = identifierPattern.matcher(line.substring(currentIndex))
                    if (identifierMatcher.find()) {
                        val identifier = identifierMatcher.group()
                        tokens.add(Token(TokenType.Identifier, identifier, currentLine, currentIndex))
                        currentIndex += identifier.length
                        continue
                    }
                }

                // Try to match a number
                val numberPattern = Pattern.compile("^\\d+(\\.\\d+)?")
                val numberMatcher = numberPattern.matcher(line.substring(currentIndex))
                if (numberMatcher.find()) {
                    val string = numberMatcher.group()
                    tokens.add(Token(TokenType.Number, string, currentLine, currentIndex))
                    currentIndex += string.length
                    continue
                }

                // Try to match a numerical operator
                val numericOperatorPattern = Pattern.compile("^[+\\-*/]")
                val numericOperatorMatcher = numericOperatorPattern.matcher(line.substring(currentIndex))
                if (numericOperatorMatcher.find()) {
                    val operator = numericOperatorMatcher.group()
                    tokens.add(Token(TokenType.NumericalOperator, operator, currentLine, currentIndex))
                    currentIndex += operator.length
                    continue
                }

                // Try to match a logical operator
                val logicalOperatorsPattern = Pattern.compile("^(&&|\\|\\||[<>]=?|!=|!|==|\\^)")
                val logicalOperatorMatcher = logicalOperatorsPattern.matcher(line.substring(currentIndex))
                if (logicalOperatorMatcher.find()) {
                    val operator = logicalOperatorMatcher.group()
                    tokens.add(Token(TokenType.LogicalOperator, operator, currentLine, currentIndex))
                    currentIndex += operator.length
                    continue
                }

                // Try to match a for-like conditional
                val forOperatorPattern = Pattern.compile("^\\.\\.")
                val forOperatorMatcher = forOperatorPattern.matcher(line.substring(currentIndex))
                if (forOperatorMatcher.find()) {
                    val operator = forOperatorMatcher.group()
                    tokens.add(Token(TokenType.ForCondition, operator, currentLine, currentIndex))
                    currentIndex += operator.length
                    continue
                }

                // Try to match an open curly brace
                if (line[currentIndex] == '=') {
                    tokens.add(Token(TokenType.Equals, "=", currentLine, currentIndex))
                    currentIndex ++
                    continue
                }

                // Try to match an open curly brace
                if (line[currentIndex] == '{') {
                    tokens.add(Token(TokenType.OpenBrace, "{", currentLine, currentIndex))
                    currentIndex ++
                    continue
                }

                // Try to match an open curly brace
                if (line[currentIndex] == '}') {
                    tokens.add(Token(TokenType.CloseBrace, "}", currentLine, currentIndex))
                    currentIndex ++
                    continue
                }

                // Try to match an open curly brace
                if (line[currentIndex] == '(') {
                    tokens.add(Token(TokenType.OpenParenthesis, "(", currentLine, currentIndex))
                    currentIndex ++
                    continue
                }

                // Try to match an open curly brace
                if (line[currentIndex] == ')') {
                    tokens.add(Token(TokenType.CloseParenthesis, ")", currentLine, currentIndex))
                    currentIndex ++
                    continue
                }

                // Try to match a string
                val stringPattern = Pattern.compile("^'[^']*'")
                val stringMatcher = stringPattern.matcher(line.substring(currentIndex))
                if (stringMatcher.find()) {
                    val string = stringMatcher.group()
                    tokens.add(Token(TokenType.String, string, currentLine, currentIndex))
                    currentIndex += string.length
                    continue
                }

                // If no token matched, report an invalid token
                val invalidChar = line[currentIndex].toString()
                tokens.add(Token(TokenType.Invalid, invalidChar, currentLine, currentIndex))
                currentIndex++
                exitCode++
            }

            currentIndex = 0
            currentLine++
        }

        val resultMap = HashMap<String, Any>()
        resultMap["exitcode"] = exitCode
        resultMap["tokens"] = tokens
        return resultMap
    }
}

