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

package uk.gov.hmrc.gform.core

import cats.syntax.either._
import org.scalatest.{ EitherValues, FlatSpec, Matchers }
import play.api.libs.json._
import uk.gov.hmrc.gform.exceptions.InvalidState
import uk.gov.hmrc.gform.models._

class FormValidatorSpec extends FlatSpec with Matchers with EitherValues {

  "TemplateValidator.conform" should "validate form agains form schema" in {

    val formSchema =
      """|{
         |  "$schema": "http://json-schema.org/draft-04/schema#",
         |  "type": "object",
         |  "properties": {
         |    "formTypeId": {
         |      "type": "string"
         |    },
         |    "version": {
         |      "type": "string"
         |    },
         |    "characterSet": {
         |      "type": "string"
         |    },
         |    "fields": {
         |      "type": "array",
         |      "items": {
         |        "type": "object",
         |        "properties": {
         |          "id": {
         |            "type": "string"
         |          },
         |          "value": {
         |            "type": "string"
         |          }
         |        },
         |        "required": [
         |          "id",
         |          "value"
         |        ]
         |      }
         |    }
         |  },
         |  "required": [
         |    "formTypeId",
         |    "version",
         |    "characterSet",
         |    "fields"
         |  ]
         |}""".stripMargin

    val formReq =
      """|{
         |  "formTypeId": "IPT100",
         |  "version": "0.1.0",
         |  "characterSet": "UTF-8",
         |  "fields": [
         |    {
         |      "id": "iptRegNum",
         |      "value": "12AB3456780"
         |    }, {
         |      "id": "firstName",
         |      "value": "John"
         |    }, {
         |      "id": "lastName",
         |      "value": "Doe"
         |    }, {
         |      "id": "telephoneNumber",
         |      "value": "+44 (01273) 123456"
         |    }, {
         |      "id": "nameOfBusiness",
         |      "value": "Acme Widgets Ltd."
         |    }, {
         |      "id": "accountingPeriodStartDate",
         |      "value": "2015-08-01"
         |    }, {
         |      "id": "accountingPeriodEndDate",
         |      "value": "2015-12-01"
         |    }, {
         |      "id": "standardRateIPTDueForThisPeriod",
         |      "value": "1329345.49"
         |    }, {
         |      "id": "higherRateIPTDueForThisPeriod",
         |      "value": "58373265.23"
         |    }
         |  ]
         |}""".stripMargin

    val schemaRes = SchemaValidator.conform(Json.parse(formSchema).as[Schema])

    val finalRes =
      for {
        schema <- schemaRes
        res <- schema.conform(Json.parse(formReq)).toEither
      } yield res

    finalRes.right.value should be(())
  }

  def getMandatoryFieldValue(id: String) = FieldValue(
    id = FieldId(id),
    `type` = Text(Constant(""), total = false),
    label = "",
    helpText = None,
    editable = true,
    mandatory = true,
    submissible = true
  )

  def getAddressFieldValue(id: String) = FieldValue(
    id = FieldId(id),
    `type` = Address,
    label = "",
    helpText = None,
    editable = true,
    mandatory = false,
    submissible = true
  )

  "FormValidator.conform" should "parse all fields from form to list of FormField objects" in {

    val formReq =
      """|{
         |  "formTypeId": "IPT100",
         |  "version": "0.1.0",
         |  "characterSet": "UTF-8",
         |  "fields": [
         |    {
         |      "id": "iptRegNum",
         |      "value": "12AB3456780"
         |    }, {
         |      "id": "firstName",
         |      "value": "John"
         |    }, {
         |      "id": "lastName",
         |      "value": "Doe"
         |    }, {
         |      "id": "telephoneNumber",
         |      "value": "+44 (01273) 123456"
         |    }, {
         |      "id": "nameOfBusiness",
         |      "value": "Acme Widgets Ltd."
         |    }, {
         |      "id": "accountingPeriodStartDate",
         |      "value": "2015-08-01"
         |    }, {
         |      "id": "accountingPeriodEndDate",
         |      "value": "2015-12-01"
         |    }, {
         |      "id": "standardRateIPTDueForThisPeriod",
         |      "value": "1329345.49"
         |    }, {
         |      "id": "higherRateIPTDueForThisPeriod",
         |      "value": "58373265.23"
         |    }
         |  ]
         |}""".stripMargin

    val res = FormValidator.conform(Json.parse(formReq))

    res.right.value should be(
      List(
        FormField(FieldId("iptRegNum"), "12AB3456780"),
        FormField(FieldId("firstName"), "John"),
        FormField(FieldId("lastName"), "Doe"),
        FormField(FieldId("telephoneNumber"), "+44 (01273) 123456"),
        FormField(FieldId("nameOfBusiness"), "Acme Widgets Ltd."),
        FormField(FieldId("accountingPeriodStartDate"), "2015-08-01"),
        FormField(FieldId("accountingPeriodEndDate"), "2015-12-01"),
        FormField(FieldId("standardRateIPTDueForThisPeriod"), "1329345.49"),
        FormField(FieldId("higherRateIPTDueForThisPeriod"), "58373265.23")
      )
    )
  }

  "Validation of form fields" should "succeed" in {

    val formFields =
      List(
        FormField(FieldId("iptRegNum"), "12AB3456780"),
        FormField(FieldId("firstName"), "John"),
        FormField(FieldId("lastName"), "Doe"),
        FormField(FieldId("telephoneNumber"), "+44 (01273) 123456"),
        FormField(FieldId("nameOfBusiness"), "Acme Widgets Ltd.")
      )

    val section = Section(
      title = "",
      fields = List("iptRegNum", "firstName", "lastName", "telephoneNumber", "nameOfBusiness").map(getMandatoryFieldValue)
    )

    val res = FormValidator.validate(formFields, section)

    res.right.value should be(())

  }

  it should "succeed with address component" in {

    val formFields =
      List(
        FormField(FieldId("iptRegNum"), "12AB3456780"),
        FormField(FieldId("firstName"), "John"),
        FormField(FieldId("lastName"), "Doe"),
        FormField(FieldId("telephoneNumber"), "+44 (01273) 123456"),
        FormField(FieldId("nameOfBusiness"), "Acme Widgets Ltd."),
        FormField(FieldId("homeAddress.street1"), "1"),
        FormField(FieldId("homeAddress.street2"), "2"),
        FormField(FieldId("homeAddress.street3"), "3"),
        FormField(FieldId("homeAddress.town"), "4"),
        FormField(FieldId("homeAddress.county"), "5"),
        FormField(FieldId("homeAddress.postcode"), "6"),
        FormField(FieldId("homeAddress.country"), "7")
      )

    val section = Section(
      title = "",
      fields = getAddressFieldValue("homeAddress") :: List("iptRegNum", "firstName", "lastName", "telephoneNumber", "nameOfBusiness").map(getMandatoryFieldValue)
    )

    val res = FormValidator.validate(formFields, section)

    res.right.value should be(())

  }

  it should "fail if form contains field not defined in template fields" in {

    val formFields =
      List(
        FormField(FieldId("iptRegNum"), "12AB3456780")
      )

    val section = Section(
      title = "",
      fields = List.empty[FieldValue]
    )

    val res = FormValidator.validate(formFields, section)

    res.left.value should be(InvalidState("Field iptRegNum is not part of the template"))

  }

  it should "fail if form don't contains field which is mandatory" in {

    val formFields = List.empty[FormField]

    val section = Section(
      title = "",
      fields = List("iptRegNum").map(getMandatoryFieldValue)
    )

    val res = FormValidator.validate(formFields, section)

    res.left.value should be(InvalidState("Required fields iptRegNum are missing in form submission."))

  }
}
