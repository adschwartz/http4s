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

package org.http4s.parser

import java.nio.charset.{Charset, StandardCharsets}
import java.util.Locale

import org.http4s.headers.Forwarded
import org.http4s.internal.bug
import org.http4s.internal.parboiled2._
import org.http4s.internal.parboiled2.support._
import org.http4s.{ParseResult, Uri => UriModel}

private[parser] trait ForwardedHeader {
  def FORWARDED(value: String): ParseResult[Forwarded] = new Parser(value).parse

  private class Parser(value: String)
      extends Http4sHeaderParser[Forwarded](value)
      with Rfc3986Parser {

    override def charset: Charset = StandardCharsets.ISO_8859_1

    override def entry: Rule1[Forwarded] =
      rule {
        oneOrMore(ELEMENT).separatedBy(ListSep) ~ EOL ~> { (elems: Seq[Forwarded.Element]) =>
          Forwarded(elems.head, elems.tail: _*)
        }
      }

    private type MaybeElement = Option[Forwarded.Element]

    private def ELEMENT: Rule1[Forwarded.Element] =
      rule {
        push(None) ~ oneOrMore(Param).separatedBy(';') ~> {
          (_: MaybeElement).getOrElse(throw bug("empty value should not occur here"))
        }
      }

    private type ParamRule = Rule[MaybeElement :: HNil, MaybeElement :: HNil]

    private def Param: ParamRule =
      rule {
        Token ~> { (token: String) =>
          token.toLowerCase(Locale.ROOT) match {
            case "by" => CreateOrAssignParam_by
            case "for" => CreateOrAssignParam_for
            case "host" => CreateOrAssignParam_host
            case "proto" => CreateOrAssignParam_proto
            case other => failX(s"parameters: 'by', 'for', 'host' or 'proto', but got '$other'")
          }
        }
      }

    private def CreateOrAssignParam_by =
      CreateOrAssignParam(
        "by",
        Forwarded.Node.fromString,
        Forwarded.Element.fromBy,
        _.maybeBy,
        _.withBy)

    private def CreateOrAssignParam_for =
      CreateOrAssignParam(
        "for",
        Forwarded.Node.fromString,
        Forwarded.Element.fromFor,
        _.maybeFor,
        _.withFor)

    private def CreateOrAssignParam_host =
      CreateOrAssignParam(
        "host",
        Forwarded.Host.fromString,
        Forwarded.Element.fromHost,
        _.maybeHost,
        _.withHost)

    private def CreateOrAssignParam_proto =
      CreateOrAssignParam(
        "proto",
        UriModel.Scheme.fromString,
        Forwarded.Element.fromProto,
        _.maybeProto,
        _.withProto)

    private def CreateOrAssignParam[A](
        name: String,
        parse: String => ParseResult[A],
        create: A => Forwarded.Element,
        access: Forwarded.Element => Option[A],
        assign: Forwarded.Element => A => Forwarded.Element): ParamRule =
      rule {
        '=' ~ Value ~> { (maybeElem: MaybeElement, value: String) =>
          parse(value) match {
            case Left(failure) => failX(failure.sanitized)
            case Right(value) =>
              maybeElem match {
                case None =>
                  push(Some(create(value)))
                case Some(elem) if access(elem).isEmpty =>
                  push(Some(assign(elem).apply(value)))
                case _ =>
                  failX(s"'$name' must not occur more than once within a single element")
              }
          }
        }
      }
  }
}
