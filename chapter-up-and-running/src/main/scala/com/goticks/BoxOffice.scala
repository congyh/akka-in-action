package com.goticks

import scala.concurrent.Future

import akka.actor._
import akka.util.Timeout

object BoxOffice {
  // Note: Props is the apply method defined in specific object.
  // Note: new BoxOffice is the
  def props(implicit timeout: Timeout) = Props(new BoxOffice)
  def name = "boxOffice"

  // Note: name and event below are the same meaning if Event exists
  // Note: name is used when we do event operation.
  // Note: event is used when we do ticket operation.
  // Note: Anyway, we will check if the existing of Event before event or
  // Note: ticket operation.
  case class CreateEvent(name: String, tickets: Int)
  case class GetEvent(name: String)
  case object GetEvents
  case class GetTickets(event: String, tickets: Int)
  case class CancelEvent(name: String)

  case class Event(name: String, tickets: Int)
  case class Events(events: Vector[Event])

  sealed trait EventResponse
  case class EventCreated(event: Event) extends EventResponse
  case object EventExists extends EventResponse

}

class BoxOffice(implicit timeout: Timeout) extends Actor {
  import BoxOffice._
  import context._


  def createTicketSeller(name: String) =
    // Note: context is the running context when actor receiving some event.
    // Note: context contains things like event sender.
    context.actorOf(TicketSeller.props(name), name)

  def receive = {
    case CreateEvent(name, tickets) =>
      def create() = {
        val eventTickets = createTicketSeller(name)
        val newTickets = (1 to tickets).map { ticketId =>
          TicketSeller.Ticket(ticketId)
        }.toVector
        eventTickets ! TicketSeller.Add(newTickets)
        sender() ! EventCreated(Event(name, tickets))
      }
      // Note: child(name) will return an child ActorRef of Optional form.
      // Note: fold: create one if not exists or call sender that event exists.
      context.child(name).fold(create())(_ => sender() ! EventExists)



    case GetTickets(event, tickets) =>
      def notFound() = sender() ! TicketSeller.Tickets(event)
      def buy(child: ActorRef) =
        // Note: forward means the actor itself behaves like a proxy between sender and child,
        // Note: The response of child will be directly send to sender.
        child.forward(TicketSeller.Buy(tickets)) // Note: When code reach here, the child ActorRef is already present.

      context.child(event).fold(notFound())(buy)


    case GetEvent(event) =>
      def notFound() = sender() ! None
      def getEvent(child: ActorRef) = child forward TicketSeller.GetEvent
      context.child(event).fold(notFound())(getEvent)


    case GetEvents =>
      import akka.pattern.ask
      import akka.pattern.pipe

      def getEvents = context.children.map { child =>
        self.ask(GetEvent(child.path.name)).mapTo[Option[Event]]
      }
      def convertToEvents(f: Future[Iterable[Option[Event]]]) =
        f.map(_.flatten).map(l=> Events(l.toVector))

      pipe(convertToEvents(Future.sequence(getEvents))) to sender()


    case CancelEvent(event) =>
      def notFound() = sender() ! None
      def cancelEvent(child: ActorRef) = child forward TicketSeller.Cancel
      context.child(event).fold(notFound())(cancelEvent)
  }
}
