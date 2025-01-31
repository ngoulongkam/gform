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

import cats.data.NonEmptyList
import cats.syntax.either._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.gform.core._
import uk.gov.hmrc.gform.exceptions.{ InvalidState, UnexpectedState }

case class FieldValue(
  id: FieldId,
  `type`: ComponentType,
  label: String,
  helpText: Option[String],
  mandatory: Boolean,
  editable: Boolean,
  submissible: Boolean
)

object FieldValue {

  implicit val formatFieldValueRaw: OFormat[FieldValueRaw] = (
    (__ \ 'id).format[FieldId] and
    (__ \ 'type).formatNullable[ComponentTypeRaw] and
    (__ \ 'label).format[String] and
    (__ \ 'value).formatNullable[ValueExpr] and
    (__ \ 'format).formatNullable[FormatExpr] and
    (__ \ 'helpText).formatNullable[String] and
    (__ \ 'optionHelpText).formatNullable[List[String]] and
    (__ \ 'submitMode).formatNullable[String] and
    (__ \ 'choices).formatNullable[List[String]] and
    (__ \ 'fields).lazyFormatNullable(implicitly[Format[List[FieldValueRaw]]]) and
    (__ \ 'mandatory).formatNullable[String] and
    (__ \ 'offset).formatNullable[Offset] and
    (__ \ 'multivalue).formatNullable[String] and
    (__ \ 'total).formatNullable[String]
  )(FieldValueRaw.apply, unlift(FieldValueRaw.unapply))

  implicit val format: OFormat[FieldValue] = {
    implicit val formatFieldValue = Json.format[FieldValue]

    val reads: Reads[FieldValue] = (formatFieldValue: Reads[FieldValue]) |
      (formatFieldValueRaw: Reads[FieldValueRaw]).flatMap(_.toFieldValue)

    OFormat[FieldValue](reads, formatFieldValue)
  }
}

case class Mandatory(val value: Boolean) extends AnyVal
case class Editable(val value: Boolean) extends AnyVal
case class Submissible(val value: Boolean) extends AnyVal

case class FieldValueRaw(
    id: FieldId,
    `type`: Option[ComponentTypeRaw] = None,
    label: String,
    value: Option[ValueExpr] = None,
    format: Option[FormatExpr] = None,
    helpText: Option[String] = None,
    optionHelpText: Option[List[String]] = None,
    submitMode: Option[String] = None,
    choices: Option[List[String]] = None,
    fields: Option[List[FieldValueRaw]] = None,
    mandatory: Option[String] = None,
    offset: Option[Offset] = None,
    multivalue: Option[String] = None,
    total: Option[String] = None
) {

  private def getFieldValue(): Either[UnexpectedState, FieldValue] = {

    val optMandEditSubm: Either[UnexpectedState, (Mandatory, Editable, Submissible)] = (submitMode, mandatory) match {
      case (Some(IsStandard()) | None,
        Some(IsTrueish()) | None) =>
        Right((Mandatory(true), Editable(true), Submissible(true)))
      case (Some(IsReadOnly()),
        Some(IsTrueish()) | None) =>
        Right((Mandatory(true), Editable(false), Submissible(true)))
      case (Some(IsInfo()),
        Some(IsTrueish()) | None) =>
        Right((Mandatory(true), Editable(false), Submissible(false)))
      case (Some(IsStandard()) | None,
        Some(IsFalseish())) =>
        Right((Mandatory(false), Editable(true), Submissible(true)))
      case (Some(IsInfo()),
        Some(IsFalseish())) =>
        Right((Mandatory(false), Editable(false), Submissible(false)))
      case otherwise => Left(InvalidState(s"Expected 'standard', 'readonly' or 'info' string or nothing for submitMode and expected 'true' or 'false' string or nothing for mandatory field value, got: $otherwise"))
    }

    val optFieldValue: Either[UnexpectedState, FieldValue] = optMandEditSubm match {
      case Right((m, e, s)) => {
        val optFv: Either[UnexpectedState, FieldValue] = toComponentType.flatMap {
          ct =>
            Right(FieldValue(
              id = id,
              `type` = ct,
              label = label,
              helpText = helpText,
              mandatory = m.value,
              editable = e.value,
              submissible = s.value
            ))
        }
        optFv
      }
      case Left(ue) => Left(ue)
    }

    optFieldValue

  }

  private val toComponentType: Opt[ComponentType] = `type` match {
    case Some(TextRaw) | None =>
      (value, total) match {
        case (Some(TextExpression(expr)), IsTotal(TotalYes)) => Right(Text(expr, total = true))
        case (Some(TextExpression(expr)), IsTotal(TotalNo)) => Right(Text(expr, total = false))
        case (None, IsTotal(TotalYes)) => Right(Text(Constant(""), total = true))
        case (None, IsTotal(TotalNo)) => Right(Text(Constant(""), total = false))
        case (Some(invalidValue), invalidTotal) => Left(
          InvalidState(s"""|Unsupported type of value for text field
                           |Id: $id
                           |Value: $invalidValue
                           |Total: $invalidTotal""".stripMargin)
        )
      }
    case Some(DateRaw) =>
      val finalOffset = offset.getOrElse(Offset(0))

      val valueOpt =
        value match {
          case Some(DateExpression(dateExpr)) => Right(Some(dateExpr))
          case None => Right(None)
          case Some(invalidValue) => Left(
            InvalidState(s"""|Unsupported type of value for date field
                             |Id: $id
                             |Value: $invalidValue""".stripMargin)
          )
        }

      val formatOpt =
        format match {
          case Some(DateFormat(format)) => Right(format)
          case None => Right(AnyDate)
          case Some(invalidFormat) => Left(
            InvalidState(s"""|Unsupported type of format for date field
                             |Id: $id
                             |Format: $invalidFormat""".stripMargin)
          )
        }

      for {
        value <- valueOpt
        format <- formatOpt
      } yield Date(format, finalOffset, value)

    case Some(AddressRaw) => Right(Address)

    case Some(GroupRaw) => {
      fields match {
        case Some(fvrs) => {

          val unexpectedStateOrFieldValues: List[Either[UnexpectedState, FieldValue]] = fvrs.map {
            case (fvr) => {
              val fieldValueOpt: Either[UnexpectedState, FieldValue] = fvr.getFieldValue
              fieldValueOpt
            }
          }
          val unexpectedStateOrGroup: Either[UnexpectedState, Group] = unexpectedStateOrFieldValues.partition(_.isRight) match {
            case (ueorfvs, Nil) => {
              Right(Group((ueorfvs).collect { case Right(fv) => fv }))
            }
            case (_, ueorfvs) => {
              val unexpectedStates: List[UnexpectedState] = ueorfvs.collect { case (Left(ue)) => ue }
              Left(unexpectedStates.head)
            }
          }
          unexpectedStateOrGroup
        }
        case _ => Left(InvalidState(s"""Require 'fields' element in Group"""))
      }
    }

    case Some(ChoiceRaw) =>
      (format, choices, multivalue, value, optionHelpText) match {
        case (IsOrientation(VerticalOrientation), Some(x :: xs), IsMultivalue(MultivalueYes), Selections(selections), optionHelpText) =>
          Right(Choice(Checkbox, NonEmptyList(x, xs), Vertical, selections, optionHelpText))
        case (IsOrientation(VerticalOrientation), Some(x :: xs), IsMultivalue(MultivalueNo), Selections(selections), optionHelpText) =>
          Right(Choice(Radio, NonEmptyList(x, xs), Vertical, selections, optionHelpText))
        case (IsOrientation(HorizontalOrientation), Some(x :: xs), IsMultivalue(MultivalueYes), Selections(selections), optionHelpText) =>
          Right(Choice(Checkbox, NonEmptyList(x, xs), Horizontal, selections, optionHelpText))
        case (IsOrientation(HorizontalOrientation), Some(x :: xs), IsMultivalue(MultivalueNo), Selections(selections), optionHelpText) =>
          Right(Choice(Radio, NonEmptyList(x, xs), Horizontal, selections, optionHelpText))
        case (IsOrientation(YesNoOrientation), None, IsMultivalue(MultivalueNo), Selections(selections), optionHelpText) =>
          Right(Choice(YesNo, NonEmptyList.of("Yes", "No"), Horizontal, selections, optionHelpText))
        case (IsOrientation(YesNoOrientation), _, _, Selections(selections), optionHelpText) =>
          Right(Choice(YesNo, NonEmptyList.of("Yes", "No"), Horizontal, selections, optionHelpText))
        case (invalidFormat, invalidChoices, invalidMultivalue, invalidValue, invalidHelpText) => Left(
          InvalidState(s"""|Unsupported combination of 'format, choices, multivalue and value':
                           |Format     : $invalidFormat
                           |Choices    : $invalidChoices
                           |Multivalue : $invalidMultivalue
                           |Value      : $invalidValue
                           |optionHelpText: $invalidHelpText
                           |""".stripMargin)
        )
      }
  }

  private final object Selections {
    def unapply(choiceExpr: Option[ValueExpr]): Option[List[Int]] = {
      choiceExpr match {
        case Some(ChoiceExpression(selections)) => Some(selections)
        case None => Some(List.empty[Int])
        case Some(_) => None
      }
    }
  }

  private sealed trait OrientationValue

  private final case object VerticalOrientation extends OrientationValue
  private final case object HorizontalOrientation extends OrientationValue
  private final case object YesNoOrientation extends OrientationValue

  private final object IsOrientation {
    def unapply(orientation: Option[FormatExpr]): Option[OrientationValue] = {
      orientation match {
        case Some(TextFormat("vertical")) | None => Some(VerticalOrientation)
        case Some(TextFormat("horizontal")) => Some(HorizontalOrientation)
        case Some(TextFormat("yesno")) => Some(YesNoOrientation)
        case _ => None
      }
    }
  }

  private sealed trait Total

  private final case object TotalYes extends Total
  private final case object TotalNo extends Total

  private final object IsTotal {
    def unapply(total: Option[String]): Option[Total] = {
      total match {
        case Some(IsFalseish()) | None => Some(TotalNo)
        case Some(IsTrueish()) => Some(TotalYes)
        case _ => None
      }
    }
  }

  private sealed trait Multivalue

  private final case object MultivalueYes extends Multivalue
  private final case object MultivalueNo extends Multivalue

  private final object IsMultivalue {
    def unapply(multivalue: Option[String]): Option[Multivalue] = {
      multivalue match {
        case Some(IsFalseish()) | None => Some(MultivalueNo)
        case Some(IsTrueish()) => Some(MultivalueYes)
        case _ => None
      }
    }
  }

  def toFieldValue = Reads[FieldValue] { _ =>

    getFieldValue() match {
      case Right(fieldValue) => JsSuccess(fieldValue)
      case Left(error) => JsError(error.toString)
    }
  }

  // Should we instead do this with an case insensitive extractor
  object IsStandard {
    def unapply(maybeStandard: String): Boolean = maybeStandard.toLowerCase == "standard"
  }
  object IsReadOnly {
    def unapply(maybeStandard: String): Boolean = maybeStandard.toLowerCase == "readonly"
  }
  object IsInfo {
    def unapply(maybeStandard: String): Boolean = maybeStandard.toLowerCase == "info"
  }

  object IsTrueish {
    def unapply(maybeBoolean: String): Boolean = {
      maybeBoolean.toLowerCase match {
        case "true" | "yes" => true
        case _ => false
      }
    }
  }
  object IsFalseish {
    def unapply(maybeBoolean: String): Boolean = {
      maybeBoolean.toLowerCase match {
        case "false" | "no" => true
        case _ => false
      }
    }
  }

}