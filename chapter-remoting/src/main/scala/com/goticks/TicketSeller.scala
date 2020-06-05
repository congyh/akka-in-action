package com.goticks

import akka.actor.{ Actor, Props, PoisonPill }
object TicketSeller {
  def props(event: String) = Props(new TicketSeller(event))

  case class Add(tickets: Vector[Ticket])
  case class Buy(tickets: Int)
  case class Ticket(id: Int)
  case class Tickets(event: String,
                     entries: Vector[Ticket] = Vector.empty[Ticket])
  case object GetEvent
  case object Cancel

}


class TicketSeller(event: String) extends Actor {
  import TicketSeller._

  var tickets = Vector.empty[Ticket] // Note: Vector is an immutable list.

  def receive = {
    case Add(newTickets) => tickets = tickets ++ newTickets // Note: Return a new immutable list.
    case Buy(nrOfTickets) =>
      val entries = tickets.take(nrOfTickets).toVector
      if(entries.size >= nrOfTickets) {
        // Note: TODO: Shouldn't the operations below be wrapped in a transaction?
        // Note: ! is a method define in ActorRef to send msg asynchronously.
        sender() ! Tickets(event, entries) // Note: sender() returns the ref of the msg sender.
        tickets = tickets.drop(nrOfTickets)
        // Note: Whether return 200 or 404 is handled in route layer, which is RestApi in this instance.
      } else sender() ! Tickets(event)
    case GetEvent => sender() ! Some(BoxOffice.Event(event, tickets.size))
    case Cancel =>
      sender() ! Some(BoxOffice.Event(event, tickets.size))
      self ! PoisonPill // Note: Kill itself.
  }
}

