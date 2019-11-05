package magnolify.tensorflow

import java.{lang => jl, util => ju}

import com.google.protobuf.ByteString
import magnolia._
import magnolify.shared.Converter
import magnolify.shims._
import org.tensorflow.example.{BytesList, Example, Feature, Features, FloatList, Int64List}

import scala.collection.JavaConverters._
import scala.language.experimental.macros

sealed trait ExampleType[T] extends Converter[T, Example, Example.Builder] {
  def apply(v: Example): T = from(v)
  def apply(v: T): Example = to(v).build()
}

object ExampleType {
  implicit def apply[T](implicit f: ExampleField.Record[T]): ExampleType[T] = new ExampleType[T] {
    override def from(v: Example): T = f.get(v.getFeatures, null)
    override def to(v: T): Example.Builder =
      Example.newBuilder().setFeatures(f.put(Features.newBuilder(), null, v))
  }
}

sealed trait ExampleField[T] extends Serializable { self =>
  def get(f: Features, k: String): T
  def put(f: Features.Builder, k: String, v: T): Features.Builder
}

object ExampleField {
  trait Primitive[T] extends ExampleField[T] {
    type ValueT
    def fromFeature(v: Feature): ju.List[T]
    def toFeature(v: Iterable[T]): Feature

    def fromValue(v: ValueT): T
    def toValue(v: T): ValueT

    def fromValues(v: ju.List[ValueT]): ju.List[T] = v.asScala.map(fromValue).asJava
    def toValues(v: Iterable[T]): Iterable[ValueT] = v.map(toValue)

    override def get(f: Features, k: String): T = {
      val l = fromFeature(f.getFeatureOrDefault(k, null))
      require(l.size() == 1)
      l.get(0)
    }

    override def put(f: Features.Builder, k: String, v: T): Features.Builder =
      f.putFeature(k, toFeature(Iterable(v)))
  }

  trait Record[T] extends ExampleField[T]

  //////////////////////////////////////////////////

  type Typeclass[T] = ExampleField[T]

  def combine[T](caseClass: CaseClass[Typeclass, T]): Record[T] = new Record[T] {
    private def key(prefix: String, label: String): String =
      if (prefix == null) label else s"$prefix.$label"

    override def get(f: Features, k: String): T =
      caseClass.construct { p =>
        p.typeclass.get(f, key(k, p.label))
      }

    override def put(f: Features.Builder, k: String, v: T): Features.Builder =
      caseClass.parameters.foldLeft(f) { (f, p) =>
        p.typeclass.put(f, key(k, p.label), p.dereference(v))
        f
      }
  }

  def dispatch[T](sealedTrait: SealedTrait[Typeclass, T]): Record[T] = ???

  implicit def gen[T]: Record[T] = macro Magnolia.gen[T]

  //////////////////////////////////////////////////

  def apply[T](implicit f: ExampleField[T]): ExampleField[T] = f

  def from[T]: FromWord[T] = new FromWord

  class FromWord[T] {
    def apply[U](f: T => U)(g: U => T)(implicit ef: Primitive[T]): Primitive[U] =
      new Primitive[U] {
        override type ValueT = ef.ValueT
        override def fromFeature(v: Feature): ju.List[U] =
          ef.fromFeature(v).asScala.map(f).asJava
        override def toFeature(v: Iterable[U]): Feature = ef.toFeature(v.map(g))
        override def fromValue(v: ValueT): U = f(ef.fromValue(v))
        override def toValue(v: U): ValueT = ef.toValue(g(v))
      }
  }

  implicit val efLong = new Primitive[Long] {
    override type ValueT = jl.Long
    override def fromFeature(v: Feature): ju.List[Long] =
      if (v == null) {
        java.util.Collections.emptyList()
      } else {
        fromValues(v.getInt64List.getValueList)
      }

    override def toFeature(v: Iterable[Long]): Feature =
      Feature
        .newBuilder()
        .setInt64List(Int64List.newBuilder().addAllValue(toValues(v).asJava))
        .build()

    override def fromValue(v: jl.Long): Long = v
    override def toValue(v: Long): jl.Long = v
    override def fromValues(v: ju.List[jl.Long]): ju.List[Long] = v.asInstanceOf[ju.List[Long]]
    override def toValues(v: Iterable[Long]): Iterable[jl.Long] = v.asInstanceOf[Iterable[jl.Long]]
  }

  implicit val efFloat = new Primitive[Float] {
    override type ValueT = jl.Float
    override def fromFeature(v: Feature): ju.List[Float] =
      if (v == null) {
        java.util.Collections.emptyList()
      } else {
        fromValues(v.getFloatList.getValueList)
      }

    override def toFeature(v: Iterable[Float]): Feature =
      Feature
        .newBuilder()
        .setFloatList(FloatList.newBuilder().addAllValue(toValues(v).asJava))
        .build()

    override def fromValue(v: jl.Float): Float = v
    override def toValue(v: Float): jl.Float = v
    override def fromValues(v: ju.List[jl.Float]): ju.List[Float] = v.asInstanceOf[ju.List[Float]]
    override def toValues(v: Iterable[Float]): Iterable[jl.Float] =
      v.asInstanceOf[Iterable[jl.Float]]
  }

  implicit val efBytes = new Primitive[ByteString] {
    override type ValueT = ByteString
    override def fromFeature(v: Feature): ju.List[ByteString] =
      if (v == null) {
        java.util.Collections.emptyList()
      } else {
        fromValues(v.getBytesList.getValueList)
      }

    override def toFeature(v: Iterable[ByteString]): Feature =
      Feature
        .newBuilder()
        .setBytesList(BytesList.newBuilder().addAllValue(toValues(v).asJava))
        .build()

    override def fromValue(v: ByteString): ByteString = v
    override def toValue(v: ByteString): ByteString = v
    override def fromValues(v: ju.List[ByteString]): ju.List[ByteString] = v
    override def toValues(v: Iterable[ByteString]): Iterable[ByteString] = v
  }

  implicit def efOption[T](implicit ef: ExampleField[T]): ExampleField[Option[T]] =
    new ExampleField[Option[T]] {
      override def get(f: Features, k: String): Option[T] =
        if (f.containsFeature(k) || f.getFeatureMap.keySet().asScala.exists(_.startsWith(s"$k."))) {
          Some(ef.get(f, k))
        } else {
          None
        }

      override def put(f: Features.Builder, k: String, v: Option[T]): Features.Builder = v match {
        case None    => f
        case Some(x) => ef.put(f, k, x)
      }
    }

  implicit def efSeq[T, S[T]](
    implicit ef: Primitive[T],
    ts: S[T] => Seq[T],
    fc: FactoryCompat[T, S[T]]
  ): ExampleField[S[T]] = new ExampleField[S[T]] {
    override def get(f: Features, k: String): S[T] =
      fc.build(ef.fromFeature(f.getFeatureOrDefault(k, null)).asScala)

    override def put(f: Features.Builder, k: String, v: S[T]): Features.Builder =
      if (v.isEmpty) f else f.putFeature(k, ef.toFeature(v))
  }
}
