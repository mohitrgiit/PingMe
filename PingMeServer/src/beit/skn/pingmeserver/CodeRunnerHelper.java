package beit.skn.pingmeserver;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;

import beit.skn.classes.PushableMessage;
import beit.skn.classes.RSAEncryptorClass;

public class CodeRunnerHelper extends Thread 
{
	protected Socket socket = null;
	private String userID = null;
	private String hostname = null;
	private String userPassword = null;	
	private ObjectOutputStream objOut = null;
	private ObjectInputStream objIn = null;
	private int userPublicKey, userModulus;
	
	public String getUserID() 
	{
		return userID;
	}

	public CodeRunnerHelper(Socket s)
	{
		socket = s;		
		try 
		{
			objOut = new ObjectOutputStream(socket.getOutputStream());
			objIn = new ObjectInputStream(socket.getInputStream());
		} catch (IOException e) 
		{
			e.printStackTrace();
		}
		
	}
	
	@Override
	public void run()
	{
		PushableMessage m = null;
		String ctrl = null;
		try 
		{
			m = (PushableMessage)objIn.readObject();
			System.out.println("New user connected. Waiting for ID.");
			ctrl = m.getControl();
			if(ctrl.contentEquals(PushableMessage.CONTROL_HELLO))
			{
				userID = m.getSender().split("@")[1];
				hostname = m.getSender().split("@")[0];
			}
			String pair = (String)m.getMessageContent(); 
			userPublicKey = Integer.parseInt(pair.split(",")[0]);
			userModulus = Integer.parseInt(pair.split(",")[1]);
			System.out.println("Host " + hostname + " of user " + userID + " attempting to authenticate, public key pair (" + userPublicKey + ", " + userModulus + ")");
			m = new PushableMessage("server", PushableMessage.CONTROL_HELLO);
			m.setMessageContent(new String(RSAEncryptorClass.getPublicKey() + "," + RSAEncryptorClass.getModulus()));
			pushMessage(m);			
			m = (PushableMessage)objIn.readObject();
			userPassword = RSAEncryptorClass.decryptText((int [])m.getMessageContent()).trim();
			if(m.isEncrypted())
				userPassword = EncryptionStub.decrypt(userPassword);			
			if(DBConnect.isAuthentic(userID, userPassword, "users"))
			{
				System.out.println("User " + userID + " authenticated. Stand by for requests.");				
				m = new PushableMessage("server", PushableMessage.CONTROL_AUTHENTIC);
				userPassword = EncryptionStub.encrypt(userPassword);
				m.setMessageContent(RSAEncryptorClass.encryptText(userPassword, userModulus, userPublicKey));
				pushMessage(m);			
				
				while(true)
				{				
					m = (PushableMessage)objIn.readObject();
					System.out.println("Received packet");
					if(m.getControl().contentEquals(PushableMessage.CONTROL_PUSH))
					{
						System.out.println("Call for " + ((String)m.getMessageContent()).split("&&&")[0] + " from lat " + ((String)m.getMessageContent()).split("&&&")[1] + " long " + ((String)m.getMessageContent()).split("&&&")[2]);
						ServerMain.multicastToAgents(m, ((String)m.getMessageContent()).split("&&&")[0]);
					}					
				}
			}
			else
			{
				System.out.println("User " + userID + " failed to authenticate. Deleting entry.");
				m = new PushableMessage("server", PushableMessage.CONTROL_ABORT);
				pushMessage(m);
				ServerMain.deleteEntry(userID, "user");	
				return;
			}
		} 
		catch(SocketException se)
		{
			System.out.println("User " + userID + " disconnected from server. Deleting entry.");	
			ServerMain.deleteEntry(hostname + "@" + userID, "coderunner");
		}
		catch(EOFException e)
		{
			
		}
		catch (IOException e) 
		{			
			e.printStackTrace();
		}
		catch (ClassNotFoundException e) 
		{			
			e.printStackTrace();
		}		
		
	}

	public synchronized void pushMessage(PushableMessage m)
	{
		try		
		{
			if(m.getControl().contentEquals(PushableMessage.CONTROL_PING_CODE))
				m.setMessageContent(RSAEncryptorClass.encryptText((String)m.getMessageContent(), userModulus, userPublicKey));
			if(objOut==null)
				objOut = new ObjectOutputStream(socket.getOutputStream());
			objOut.writeObject(m);
			objOut.flush();
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}		
	}
	
	public String getHostname() 
	{
		return hostname;
	}
}
