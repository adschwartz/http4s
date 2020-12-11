/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.internal.parsing

import cats.data.NonEmptyList
import cats.parse.{Parser, Parser1}
import cats.parse.Parser.{char, charIn}
import cats.parse.Rfc5234.{alpha, digit, htab, sp}

/** Common rules defined in RFC7230
  *
  * @see [[https://tools.ietf.org/html/rfc7230#appendix-B]]
  */
private[http4s] object Rfc7230 {
  /* `tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." /
   *  "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA`
   */
  val tchar: Parser1[Char] = charIn("!#$%&'*+-.^_`|~").orElse1(digit).orElse1(alpha)

  /* `token = 1*tchar` */
  val token: Parser1[String] = tchar.rep1.string

  /* `obs-text = %x80-FF` */
  val obsText: Parser1[Char] = charIn(0x80.toChar to 0xff.toChar)

  /* `OWS = *( SP / HTAB )` */
  val ows: Parser[Unit] = (sp.orElse1(htab)).rep.void

  /* `1#element => *( "," OWS ) element *( OWS "," [ OWS element ] )` */
  def headerRep1[A](element: Parser1[A]): Parser1[NonEmptyList[A]] = {
    val prelude = (char(',') <* ows).rep
    val tailOpt = (ows.with1 *> char(',') *> (ows.with1 *> element).?).rep
    val tail = tailOpt.map(_.collect { case Some(x) => x })

    (prelude.with1 *> element ~ tail).map { case (h, t) =>
      NonEmptyList(h, t)
    }
  }
}
