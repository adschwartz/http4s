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

package org.http4s
package parser

import cats.data.NonEmptyList
import org.http4s.headers.Authorization
import org.typelevel.ci.CIString

class AuthorizationHeaderSpec extends Http4sSpec {
  def hparse(value: String) = Authorization.parse(value)

  "Authorization header" should {
    "Parse a valid OAuth2 header" in {
      val token = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "-._~+/".toSeq).mkString
      val h = Authorization(Credentials.Token(AuthScheme.Bearer, token + "="))
      hparse(h.value) must beRight(h)
    }

    "Reject an invalid OAuth2 header" in {
      val invalidTokens = Seq("f!@", "=abc", "abc d")
      forall(invalidTokens) { token =>
        val h = Authorization(Credentials.Token(AuthScheme.Bearer, token))
        hparse(h.value) must beLeft
      }
    }

    "Parse a KeyValueCredentials header" in {
      val scheme = CIString("foo")
      val params = NonEmptyList.of("abc" -> "123", Nil)
      val h = Authorization(Credentials.AuthParams(scheme, params))
      hparse(h.value) must beRight(h)
    }

    "Parse a KeyValueCredentials header unquoted" in {
      val scheme = "foo"
      val params = NonEmptyList.of("abc" -> "123")
      val h = Authorization(Credentials.AuthParams(scheme.ci, params))
      hparse("foo abc=123") must beRight(h)
    }

    "Parse a KeyValueCredentials with weird spaces" in {
      val scheme = "foo"
      hparse("foo abc = \"123 yeah\tyeah yeah\"") must beRight(
        Authorization(
          Credentials.AuthParams(scheme.ci, NonEmptyList.of("abc" -> "123 yeah\tyeah yeah"))))

      //quoted-pair
      hparse("foo abc = \"\\123\"") must beRight(
        Authorization(Credentials.AuthParams(scheme.ci, NonEmptyList.of("abc" -> "\\123"))))
    }
  }
}
