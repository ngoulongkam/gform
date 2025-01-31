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

package uk.gov.hmrc.gform.typeclasses

import play.api.libs.json.{ JsObject, Json }

import uk.gov.hmrc.gform.models.{ Form, FormTemplate, SaveAndRetrieve, Schema }
import uk.gov.hmrc.gform.repositories.{ FormRepository, FormTemplateRepository, SaveAndRetrieveRepository, SchemaRepository }

import scala.concurrent.{ ExecutionContext, Future }

trait FindOne[T] {
  def apply(selector: JsObject): Future[Option[T]]
}

object FindOne {
  implicit def schema(implicit repo: SchemaRepository, ex: ExecutionContext) = new FindOne[Schema] {
    def apply(selector: JsObject): Future[Option[Schema]] = {
      repo.findOne(selector, Json.obj())
    }
  }

  implicit def formTemplate(implicit repo: FormTemplateRepository, ex: ExecutionContext) = new FindOne[FormTemplate] {
    def apply(selector: JsObject): Future[Option[FormTemplate]] = {
      repo.findOne(selector, Json.obj())
    }
  }

  implicit def saveAndRetrieve(implicit repo: SaveAndRetrieveRepository, ex: ExecutionContext) = new FindOne[SaveAndRetrieve] {
    def apply(selector: JsObject): Future[Option[SaveAndRetrieve]] = {
      repo.retrieve(selector)
    }
  }

  implicit def form(implicit repo: FormRepository, ex: ExecutionContext) = new FindOne[Form] {
    def apply(selector: JsObject): Future[Option[Form]] = {
      repo.findOne(selector, Json.obj())
    }
  }

}
