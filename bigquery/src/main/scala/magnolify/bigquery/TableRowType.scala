/*
 * Copyright 2019 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package magnolify.bigquery

import com.google.api.services.bigquery.model.TableFieldSchema
import com.google.api.services.bigquery.model.TableRow
import com.google.api.services.bigquery.model.TableSchema
import com.google.common.io.BaseEncoding
import magnolia._
import magnolify.shared.Converter
import magnolify.shims._

import scala.collection.JavaConverters._
import scala.language.experimental.macros

sealed trait TableRowType[T] extends Converter[T, TableRow, TableRow] {
  def schema: TableSchema
  def apply(v: TableRow): T = from(v)
  def apply(v: T): TableRow = to(v)
}

object TableRowType {
  implicit def apply[T](implicit f: TableRowField.Record[T]): TableRowType[T] =
    new TableRowType[T] {
      override def schema: TableSchema = new TableSchema().setFields(f.fieldSchema.getFields)
      override def from(v: TableRow): T = f.from(v)
      override def to(v: T): TableRow = f.to(v)
    }
}

sealed trait TableRowField[T] extends Serializable { self =>
  type FromT
  type ToT

  def fieldSchema: TableFieldSchema
  def from(v: FromT): T
  def to(v: T): ToT

  def fromAny(v: Any): T = from(v.asInstanceOf[FromT])
}

object TableRowField {
  trait Aux[T, From, To] extends TableRowField[T] {
    override type FromT = From
    override type ToT = To
  }

  trait Primitive[T] extends Aux[T, Any, Any]
  trait Record[T] extends Aux[T, java.util.Map[String, AnyRef], TableRow]

  //////////////////////////////////////////////////

  type Typeclass[T] = TableRowField[T]

  def combine[T](caseClass: CaseClass[Typeclass, T]): Record[T] = new Record[T] {
    override def fieldSchema: TableFieldSchema =
      new TableFieldSchema()
        .setType("STRUCT")
        .setMode("REQUIRED")
        .setFields(caseClass.parameters.map(p => p.typeclass.fieldSchema.setName(p.label)).asJava)

    override def from(v: java.util.Map[String, AnyRef]): T =
      caseClass.construct(p => p.typeclass.fromAny(v.get(p.label)))

    override def to(v: T): TableRow =
      caseClass.parameters.foldLeft(new TableRow) { (tr, p) =>
        val f = p.typeclass.to(p.dereference(v))
        if (f != null) {
          tr.put(p.label, f)
        }
        tr
      }
  }

  def dispatch[T](sealedTrait: SealedTrait[Typeclass, T]): Record[T] = ???

  implicit def gen[T]: Record[T] = macro Magnolia.gen[T]

  //////////////////////////////////////////////////

  def apply[T](implicit f: TableRowField[T]): TableRowField[T] = f

  def from[T]: FromWord[T] = new FromWord[T]

  class FromWord[T] {
    def apply[U](f: T => U)(g: U => T)(implicit trf: TableRowField[T]): TableRowField[U] =
      new TableRowField[U] {
        override type FromT = trf.FromT
        override type ToT = trf.ToT
        override def fieldSchema: TableFieldSchema = trf.fieldSchema
        override def from(v: FromT): U = f(trf.from(v))
        override def to(v: U): ToT = trf.to(g(v))
      }
  }

  //////////////////////////////////////////////////

  private def at[T](tpe: String)(f: Any => T)(g: T => Any): TableRowField[T] = new Primitive[T] {
    override def fieldSchema: TableFieldSchema =
      new TableFieldSchema().setType(tpe).setMode("REQUIRED")
    override def from(v: Any): T = f(v)
    override def to(v: T): Any = g(v)
  }

  implicit val trfBool = at[Boolean]("BOOL")(_.toString.toBoolean)(identity)
  implicit val trfLong = at[Long]("INT64")(_.toString.toLong)(identity)
  implicit val trfDouble = at[Double]("FLOAT64")(_.toString.toDouble)(identity)
  implicit val trfString = at[String]("STRING")(_.toString)(identity)
  implicit val trfNumeric =
    at[BigDecimal]("NUMERIC")(NumericConverter.toBigDecimal)(NumericConverter.fromBigDecimal)

  implicit val trfByteArray = at[Array[Byte]]("BYTES")(
    x => BaseEncoding.base64().decode(x.toString)
  )(x => BaseEncoding.base64().encode(x))

  import TimestampConverter._
  implicit val trfInstant = at("TIMESTAMP")(toInstant)(fromInstant)
  implicit val trfDate = at("DATE")(toLocalDate)(fromLocalDate)
  implicit val trfTime = at("TIME")(toLocalTime)(fromLocalTime)
  implicit val trfDateTime = at("DATETIME")(toLocalDateTime)(fromLocalDateTime)

  implicit def trfOption[T](implicit f: TableRowField[T]): TableRowField[Option[T]] =
    new Primitive[Option[T]] {
      override def fieldSchema: TableFieldSchema = f.fieldSchema.setMode("NULLABLE")
      override def from(v: Any): Option[T] =
        if (v == null) None else Some(f.fromAny(v))
      override def to(v: Option[T]): Any = v match {
        case None    => null
        case Some(x) => f.to(x)
      }
    }

  implicit def trfSeq[T, S[T]](
    implicit f: TableRowField[T],
    ts: S[T] => Seq[T],
    fc: FactoryCompat[T, S[T]]
  ): TableRowField[S[T]] =
    new Primitive[S[T]] {
      override def fieldSchema: TableFieldSchema = f.fieldSchema.setMode("REPEATED")
      override def from(v: Any): S[T] =
        if (v == null) {
          fc.newBuilder.result()
        } else {
          fc.build(v.asInstanceOf[java.util.List[_]].asScala.iterator.map(f.fromAny))
        }
      override def to(v: S[T]): Any = if (v.isEmpty) null else v.map(f.to(_)).asJava
    }
}
