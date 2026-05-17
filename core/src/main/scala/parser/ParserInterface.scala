package parser

trait ParserInterface {
  def parse (input: Input): Output
  def reverse (input: Input): String
}
