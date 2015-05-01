package ignition.core.utils
import scala.collection.{TraversableLike, IterableLike}
import scala.collection.generic.CanBuildFrom
import scala.language.implicitConversions
import scalaz.Validation

object CollectionUtils {

  implicit class TraversableOnceImprovements[A](xs: TraversableOnce[A]) {
    def maxByOption[B](f: A => B)(implicit cmp: Ordering[B]): Option[A] = {
      if (xs.isEmpty)
        None
      else
        Option(xs.maxBy(f))
    }

    def minByOption[B](f: A => B)(implicit cmp: Ordering[B]): Option[A] = {
      if (xs.isEmpty)
        None
      else
        Option(xs.minBy(f))
    }
  }

  implicit class TraversableLikeImprovements[A, Repr](xs: TraversableLike[A, Repr]) {
    def distinctBy[B, That](f: A => B)(implicit cbf: CanBuildFrom[Repr, A, That]) = {
      val builder = cbf(xs.repr)
      val set = collection.mutable.Set.empty[B]
      xs.foreach { o =>
        val b = f(o)
        if (!set(b)) {
          set += b
          builder += o
        }
      }
      builder.result
    }
  }

  implicit class ValidatedIterableLike[T, R, Repr <: IterableLike[Validation[R, T], Repr]](seq: IterableLike[Validation[R, T], Repr]) {
    def mapSuccess[That](f: T => Validation[R, T])(implicit cbf: CanBuildFrom[Repr, Validation[R, T], That]): That = {
      seq.map({
        case scalaz.Success(v) => f(v)
        case failure => failure
      })
    }
  }

  implicit class OptionCollection(opt: Option[String]) {
    def isBlank: Boolean = {
      opt.isEmpty || opt.get.trim.isEmpty
    }

    def nonBlank: Boolean = !opt.isBlank

    def noneIfBlank: Option[String] = {
      if (opt.isBlank) None else opt
    }

  }

  // Useful to be called from java code
  def mutableMapToImmutable[K, V](map: scala.collection.mutable.Map[K, V]): Map[K, V] = {
    map.toMap
  }

  implicit class PairRDDLikeOps[K, V](iterable: Iterable[(K, V)]) {
    def groupByKey(): List[(K, Iterable[V])] = {
      iterable
        .groupBy { case (k, v) => k }
        .mapValues(_.map { case (k, v) => v })
        .toList
    }
    
    def reduceByKey(fn: (V, V) => V): List[(K, V)] = {
      iterable
        .groupBy { case (k, v) => k }
        .mapValues(_.map { case (k, v) => v }.reduce(fn))
        .toList
    }
  }
}
