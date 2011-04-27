package player.proxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import player.event.PlayerDroppedPacketEvent;
import player.event.PlayerReceivedMessageEvent;
import player.event.PlayerSentMessageEvent;
import player.gamer.Gamer;
import player.gamer.statemachine.reflex.legal.LegalGamer;
import player.request.factory.RequestFactory;
import player.request.grammar.AbortRequest;
import player.request.grammar.Request;
import player.request.grammar.StartRequest;
import player.request.grammar.StopRequest;
import util.logging.GamerLogger;
import util.observer.Event;
import util.observer.Observer;
import util.observer.Subject;
import util.reflection.ProjectSearcher;

public final class ProxyGamePlayerClient extends Thread implements Subject, Observer
{
	private final Gamer gamer;	
	private final List<Observer> observers;
	
	private Socket theConnection;
	private BufferedReader theInput;
	private PrintStream theOutput;
        
    /**
     * @param args
     * Command line arguments:
     *  ProxyGamePlayerClient gamer port
     */
    public static void main(String[] args) {
		GamerLogger.setSpilloverLogfile("spilloverLog");
        GamerLogger.log("Proxy", "Starting the ProxyGamePlayerClient program.");
        
        if (!(args.length == 2)) {
            GamerLogger.logError("Proxy", "Usage is: \n\tProxyGamePlayerClient gamer port");
            return;
        }
        
        int port = 9147;
        Gamer gamer = null;
        try {
            port = Integer.valueOf(args[1]);
        } catch(Exception e) {
            GamerLogger.logError("Proxy", args[1]+" is not a valid port.");
            return;
        }
        
        List<Class<?>> gamers = ProjectSearcher.getAllClassesThatAre(Gamer.class);
        List<String> gamerNames = new ArrayList<String>();
        if(gamerNames.size()!=gamers.size())
        {
            for(Class<?> c : gamers)
                gamerNames.add(c.getName().replaceAll("^.*\\.",""));
        }
        
        int idx = gamerNames.indexOf(args[0]);
        if (idx == -1) {
            GamerLogger.logError("Proxy", args[0] + " is not a subclass of gamer.  Valid options are:");
            for(String s : gamerNames)
                GamerLogger.logError("Proxy", "\t"+s);
            return;
        }
        
        try {
            gamer = (Gamer)(gamers.get(idx).newInstance());
        } catch(Exception ex) {
            GamerLogger.logError("Proxy", "Cannot create instance of " + args[0]);
            return;
        }       
        
        try {
            ProxyGamePlayerClient theClient = new ProxyGamePlayerClient(port, gamer);
            theClient.start();
        } catch (IOException e) {
            GamerLogger.logStackTrace("Proxy", e);
        }         
    }    
	
	public ProxyGamePlayerClient(int port, Gamer gamer) throws IOException
	{
		observers = new ArrayList<Observer>();
		
		theConnection = new Socket("127.0.0.1", port);		
        theOutput = new PrintStream(theConnection.getOutputStream());
        theInput = new BufferedReader(new InputStreamReader(theConnection.getInputStream()));
		
		this.gamer = gamer;
		gamer.addObserver(this);
	}

	public void addObserver(Observer observer)
	{
		observers.add(observer);
	}

	public void notifyObservers(Event event)
	{
		for (Observer observer : observers)
		{
			observer.observe(event);
		}
	}

	private long theCode;
	
	@Override
	public void run()
	{
		while (!isInterrupted())
		{
			try
			{
			    ProxyMessage theMessage = ProxyMessage.readFrom(theInput);
			    GamerLogger.log("Proxy", "[ProxyClient] Got message: " + theMessage);
			    String in = theMessage.theMessage;
			    theCode = theMessage.messageCode;
			    long receptionTime = theMessage.receptionTime;
				notifyObservers(new PlayerReceivedMessageEvent(in));

				Request request = new RequestFactory().create(gamer, in);
				if(request instanceof StartRequest) {
				    LegalGamer theDefaultGamer = new LegalGamer();
				    new RequestFactory().create(theDefaultGamer, in).process(1);
				    GamerLogger.startFileLogging(theDefaultGamer.getMatch(), theDefaultGamer.getRoleName().toString());
				    GamerLogger.log("Proxy", "[ProxyClient] Got message: " + theMessage);
				}
				String out = request.process(receptionTime);
				
				ProxyMessage outMessage = new ProxyMessage("DONE:" + out, theCode, 0L);
				outMessage.writeTo(theOutput);
				GamerLogger.log("Proxy", "[ProxyClient] Sent message: " + outMessage);
				notifyObservers(new PlayerSentMessageEvent(out));
				
				if(request instanceof StopRequest) {
				    GamerLogger.log("Proxy", "[ProxyClient] Got stop request, shutting down.");
				    System.exit(0);
				}
                if(request instanceof AbortRequest) {
                    GamerLogger.log("Proxy", "[ProxyClient] Got abort request, shutting down.");
                    System.exit(0);
                }				
			}
			catch (Exception e)
			{
			    GamerLogger.logStackTrace("Proxy", e);
				notifyObservers(new PlayerDroppedPacketEvent());
			}
		}
		
		GamerLogger.log("Proxy", "[ProxyClient] Got interrupted, shutting down.");
	}
	
    public void observe(Event event) {
        if(event instanceof WorkingResponseSelectedEvent) {
            WorkingResponseSelectedEvent theWorking = (WorkingResponseSelectedEvent)event;
            ProxyMessage theMessage = new ProxyMessage("WORK:" + theWorking.getWorkingResponse(), theCode, 0L);
            theMessage.writeTo(theOutput);
            GamerLogger.log("Proxy", "[ProxyClient] Sent message: " + theMessage);
        }        
    }
}
