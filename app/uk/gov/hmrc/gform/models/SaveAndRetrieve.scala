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

import play.api.libs.json._

case class SaveAndRetrieve(value: JsObject) extends AnyVal

object SaveAndRetrieve {
  val writes = Writes[SaveAndRetrieve](id => id.value)
  val reads = Reads[SaveAndRetrieve] {
    case o @ JsObject(_) => JsSuccess(SaveAndRetrieve(o))
    case otherwise => JsError(s"Invalid Save and Retrieve format, expected JsObject, got: $otherwise")
  }

  implicit val format = Format[SaveAndRetrieve](reads, writes)
}
