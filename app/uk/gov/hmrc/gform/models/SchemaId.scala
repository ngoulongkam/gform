/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.gform.models

import play.api.libs.json.{ Format, JsError, JsString, JsSuccess, Reads, Writes }

case class SchemaId(value: String) extends AnyVal {
  override def toString = value
}

object SchemaId {
  val writes = Writes[SchemaId](id => JsString(id.value))
  val reads = Reads[SchemaId] {
    case JsString(value) => JsSuccess(SchemaId(value))
    case otherwise => JsError(s"Invalid schemaId, expected JsString, got: $otherwise")
  }

  implicit val format = Format[SchemaId](reads, writes)
}
