import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;


public class Server extends Thread {
	
	static DatagramPacket receivedPacket;
	byte data[];
	byte sendInfo[];
	
	public static void main(String[] args)
	{
		
		
		//Initializes packet to receive from the intermediate
		byte data[] = new byte[512];
		receivedPacket= new DatagramPacket(data, data.length);

		try {

			DatagramSocket recSocket = createSocket(69);
			System.out.println("Opened Sockets (Server)");
			//Initialize a byte array with the base pattern
			byte[] sendInfo = {0,0,0,0};
			
		
			while(true) {
				//resets the byte array
				sendInfo[1] = 0;
				sendInfo[3] = 0;
				recSocket.receive(receivedPacket);
				data = new byte[receivedPacket.getLength()];
				System.arraycopy(receivedPacket.getData(), receivedPacket.getOffset(), data, 0, receivedPacket.getLength());
				receivedPacket.setData(data);
				if(data != null){
					Thread serverThread = new Thread();
					serverThread.start();
				}
				
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	
	public void run(){
		
		try{
			
		
		int sendPort = receivedPacket.getPort();
		System.out.println("received");
		printPacket(receivedPacket);
		//calls validatePacket method to find what pattern to use
		saveFile(receivedPacket);

		DatagramPacket sendPacket = new DatagramPacket(sendInfo, sendInfo.length, new InetSocketAddress("localhost",sendPort));
		printPacket(sendPacket);
		DatagramSocket sendSocket = new DatagramSocket(96, InetAddress.getByName("127.0.0.1"));
		sendSocket.send(sendPacket);
		sendSocket.close();

		data = new byte[512];
System.arraycopy(receivedPacket.getData(), receivedPacket.getOffset(), data, 0, receivedPacket.getLength());
		receivedPacket.setData(data);

		}catch(Exception e){
			e.printStackTrace();
		}
	}

	public static String validatePacket(DatagramPacket p) throws Exception
	{
		byte[] receivedBytes = p.getData();
		if(receivedBytes[0] == 0) {
			if(receivedBytes[receivedBytes.length-1] == 0) {
				if(receivedBytes[1] == 1) {
					return "read";
				}
				else if(receivedBytes[1] == 2) {
					return "write";
				} else {

					throw new Exception();
				}
			} else {
				throw new Exception();
			}


		} else {
			throw new Exception();
		}
	}


	//method is the same as the one in the client class
	public static void printPacket(DatagramPacket p)
	{

		byte[] receivedBytes = p.getData();
		System.out.println("Data being sent/received in bytes: ");
		for(byte element : receivedBytes) {
			System.out.print(element);
		}
		System.out.println();
		String receivedString = new String(receivedBytes);
		System.out.println("Data being sent/received: " + receivedString);
		System.out.println("from/to address: " + p.getAddress());
		System.out.println("Port Number: " + p.getPort());

	}

	//method is the same as the one in the client class
	public static DatagramSocket createSocket(int port)
	{

		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket(port, InetAddress.getByName("127.0.0.1"));
		} catch (SocketException | UnknownHostException e) {
			e.printStackTrace();
		}

		return socket;

	}

	public static void saveFile(DatagramPacket packet) throws Exception
	{

		Scanner input = new Scanner(System.in);
		System.out.println("Save file as: ");
		String filename = input.next();
		input.close();
		if(filename.equals("exit") || filename.length()==0){
			System.exit(0);
		}

		byte[] receivedBytes = packet.getData();
		FileOutputStream fileWriter = null;
		try {
    fileWriter = new FileOutputStream(filename);
    fileWriter.write(receivedBytes);
 		} finally {
    fileWriter.close();
 		}


	}



}