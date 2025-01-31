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

import play.api.libs.json._
import uk.gov.hmrc.gform.core.Opt
import uk.gov.hmrc.gform.models._

import uk.gov.hmrc.gform.repositories.{ FormRepository, FormTemplateRepository, SaveAndRetrieveRepository, SchemaRepository }

import scala.concurrent.{ ExecutionContext, Future }

trait Update[T] {
  def apply(selector: JsObject, v: T): Future[Opt[DbOperationResult]]
}

object Update {

  implicit def form(implicit repo: FormRepository, ex: ExecutionContext) = new Update[Form] {
    def apply(selector: JsObject, template: Form): Future[Opt[DbOperationResult]] = {
      repo.update(selector, template).value
    }
  }

  implicit def formTemplate(implicit repo: FormTemplateRepository, ex: ExecutionContext) = new Update[FormTemplate] {
    def apply(selector: JsObject, template: FormTemplate): Future[Opt[DbOperationResult]] = {
      repo.update(selector, template)
    }
  }

  implicit def saveForm(implicit repo: SaveAndRetrieveRepository, ex: ExecutionContext) = new Update[SaveAndRetrieve] {
    def apply(selector: JsObject, form: SaveAndRetrieve): Future[Opt[DbOperationResult]] = {

      repo.save(selector, form)
    }
  }

  implicit def schema(implicit repo: SchemaRepository, ex: ExecutionContext) = new Update[Schema] {
    def apply(selector: JsObject, template: Schema): Future[Opt[DbOperationResult]] = {

      repo.update(selector, template)
    }
  }
}
