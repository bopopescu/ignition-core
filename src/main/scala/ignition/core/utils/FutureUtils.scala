package ignition.core.utils

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object FutureUtils {

  implicit class FutureImprovements[V](future: Future[V]) {
    def toOptionOnFailure(errorHandler: (Throwable) => Option[V])(implicit ec: ExecutionContext): Future[Option[V]] = {
      future.map(Option.apply).recover { case t => errorHandler(t) }
    }
  }

  implicit class FutureGeneratorImprovements[V](generator: Iterable[() => Future[V]]){
    def toLazyIterable(batchSize: Int = 1)(implicit ec: ExecutionContext): Iterable[Future[V]] = new Iterable[Future[V]] {
      override def iterator =  new Iterator[Future[V]] {
        val generatorIterator = generator.toIterator
        var currentBatch: List[Future[V]] = List.empty
        var pos = 0

        private def batchHasBeenExhausted = pos >= currentBatch.size

        private def bringAnotherBatch() = {
          currentBatch = generatorIterator.take(batchSize).map(f => f()).toList
          pos = 0
        }

        override def hasNext: Boolean = !batchHasBeenExhausted || generatorIterator.hasNext

        override def next(): Future[V] = {
          if (!hasNext) throw new NoSuchElementException("We are empty! =(")

          if (batchHasBeenExhausted)
            bringAnotherBatch()

          val result = currentBatch(pos)
          pos += 1
          result
        }
      }
    }
  }

  implicit class FutureCollectionImprovements[V](seq: TraversableOnce[Future[V]]) {

    def collectAndTake[R](pf: PartialFunction[V, R], n: Int, maxBatchSize: Int = 10)(implicit ec: ExecutionContext): Future[List[R]] = {
      val p = Promise[List[R]]()

      val iterator = seq.toIterator

      def doIt(acc: List[R]): Unit = {
        Future.sequence(iterator.take(maxBatchSize)).onComplete {
          case Success(batch) =>
            val result = acc ++ batch.collect(pf).take(n - acc.size)
            if (result.size < n && iterator.hasNext)
              doIt(result)
            else
              p.success(result)
          case Failure(t) =>
            p.failure(t)
        }
      }

      doIt(List.empty)

      p.future
    }
  }

}
