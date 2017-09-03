package BlnService

import java.util.concurrent.locks.Lock

import akka.actor.ActorSystem
import org.apache.ignite.Ignition
import org.apache.ignite.cache.CacheAtomicityMode
import org.apache.ignite.configuration.{CacheConfiguration, IgniteConfiguration}

import scala.concurrent.Future

class DataAccess(config: IgniteConfig)(implicit system: ActorSystem) {
  implicit val executionContext = system.dispatchers.lookup("ignite-dispatcher")

  if (config.singleMode) Ignition.setClientMode(false)

  private val ignite = Ignition.start(new IgniteConfiguration)
  private val cellIdToCtnCache = {
    val cfg = new CacheConfiguration[CellId, Set[Ctn]]
    cfg.setName("CellIdToCtn")
    cfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL)
    ignite.getOrCreateCache(cfg)
  }
  private val userCache = {
    val cfg = new CacheConfiguration[Ctn, User]
    cfg.setName("User")
    cfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL)
    ignite.getOrCreateCache(cfg)
  }

  def saveUser(user: User): Future[Boolean] = Future {
    userCache.putIfAbsent(user.ctn, user)
  }

  def getCtns(cell: CellId): Future[Option[Set[Ctn]]] = Future {
    Option(cellIdToCtnCache.get(cell))
  }

  def getUsersOnCell(cell: CellId): Future[Option[Set[User]]] = Future {
    import scala.collection.JavaConversions._
    for { // todo тут есть риск рассинхрона между двумя бд, посмотреть в стороную sql join'a
      links <- Option(cellIdToCtnCache.get(cell))
      users <- Option(userCache.getAll(links).values().toSet)
    } yield users
  }

  // todo по идее когда вяжем новый должны отвязать старый
  def linkCtnWithCell(cell: CellId, ctn: Ctn): Future[Boolean] = Future {
    Option(userCache.get(ctn)).fold(false) { _ =>
      loadPattern(cellIdToCtnCache.lock(cell)) {
        val current = Option(cellIdToCtnCache.get(cell)).getOrElse(Set.empty)
        cellIdToCtnCache.put(cell, current + ctn)
        true
      }
    }
  }

  private def loadPattern[A](l: Lock)(f: => A) = {
    l.lock()
    try {
      f
    }
    finally {
      l.unlock()
    }
  }
}
