package org.move.lang;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.move.lang.MoveElementTypes.*;

%%

%{
  public _MoveLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _MoveLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

WHITESPACE=[ \n\t\r\f]
LINE_COMMENT=("//".*\n)|("//".*\Z)
BLOCK_COMMENT="/"\*(.|[ \t\n\x0B\f\r])*\*"/"
ADDRESS_LITERAL=0x[0-9a-fA-F]{1,40}
BOOL_LITERAL=(true)|(false)
INTEGER_LITERAL=0|[1-9][0-9]*
HEX_STRING_LITERAL=x\"([A-F0-9a-f]+)\"
BYTE_STRING_LITERAL=b\"(.*)\"
IDENTIFIER=[_a-zA-Z][_a-zA-Z0-9]*

%%
<YYINITIAL> {
  {WHITE_SPACE}              { return WHITE_SPACE; }

  "script"                   { return SCRIPT; }
  "address"                  { return ADDRESS; }
  "module"                   { return MODULE; }
  "public"                   { return PUBLIC; }
  "fun"                      { return FUN; }
  "acquires"                 { return ACQUIRES; }
  "resource"                 { return RESOURCE; }
  "struct"                   { return STRUCT; }
  "use"                      { return USE; }
  "as"                       { return AS; }
  "mut"                      { return MUT; }
  "copyable"                 { return COPYABLE; }
  "loop"                     { return LOOP; }
  "if"                       { return IF; }
  "else"                     { return ELSE; }
  "let"                      { return LET; }
  "continue"                 { return CONTINUE; }
  "break"                    { return BREAK; }
  "return"                   { return RETURN; }
  "abort"                    { return ABORT; }
  "copy"                     { return COPY; }
  "move"                     { return MOVE; }

  {WHITESPACE}               { return WHITESPACE; }
  {LINE_COMMENT}             { return LINE_COMMENT; }
  {BLOCK_COMMENT}            { return BLOCK_COMMENT; }
  {ADDRESS_LITERAL}          { return ADDRESS_LITERAL; }
  {BOOL_LITERAL}             { return BOOL_LITERAL; }
  {INTEGER_LITERAL}          { return INTEGER_LITERAL; }
  {HEX_STRING_LITERAL}       { return HEX_STRING_LITERAL; }
  {BYTE_STRING_LITERAL}      { return BYTE_STRING_LITERAL; }
  {IDENTIFIER}               { return IDENTIFIER; }

}

[^] { return BAD_CHARACTER; }