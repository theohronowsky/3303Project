//SYSC 3303 Group 8
package ClientFiles;

import java.io.*;
import java.net.*;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.util.Arrays;
import java.util.Scanner;

import static java.lang.System.*;
import static java.lang.System.exit;
import static java.lang.System.in;
import static java.lang.System.out;

//Client class for read/write TFTP
public class Client
{

	private static DatagramSocket socket;
	private static byte[] block = {0,0};
	private static FileOutputStream fileWriter;
	private static FileInputStream myInputStream;
	private static DatagramPacket lastPacket;
	private static int SEND_PORT = 23;
	private static int REC_PORT = 81;
	private static final int TIMEOUT = 100;
	private static final int LISTEN_PORT = 24;
	private static final String ClientPath = ".\\src\\Client\\";
	private static InetAddress address;
	private static String type = "";

	public static void main(String[] args)
	{

		try {
			out.println(InetAddress.getLocalHost());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		// Scanner used for user inputs
		Scanner reader = new Scanner(in);
		int WR;
		String filename, mode;

		out.println("Verbose mode(V) or Quiet mode(Q)?");

		type = reader.next();

		if(!type.equalsIgnoreCase("V" )&& !type.equalsIgnoreCase ("Q")){
			exit(0);
		}

		out.println("Read(1) or Write(2): ");

		WR = reader.nextInt();

		if(WR != 1 && WR != 2){
			exit(0);
		}
		out.println("Input Filename: ");

		filename = reader.next();

		if(filename.equals("exit") || filename.length()==0){
			exit(0);
		}

		out.println("What mode? (only octet is implemented)");

		mode = reader.next();

		if (!mode.equalsIgnoreCase("octet")) {
			//sends an error with code 4
			sendError(4, SEND_PORT);
			shutdown();
		}

		out.println("What's the Server's address? (aaa.bbb.ccc.ddd)");
		String a = reader.next();
		/*
		out.println("aaa: ");
		int a = reader.nextInt();
		out.println("bbb: ");
		int b = reader.nextInt();
		out.println("ccc: ");
		int c = reader.nextInt();
		out.println("ddd: ");
		int d = reader.nextInt();
		*/
		
		//gets InetAddress object from the name or ipAddresss
		try {
			//address = InetAddress.getByAddress(new byte[] {(byte)a,(byte)b,(byte)c,(byte)d});
			address = InetAddress.getByName(a);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		//close the scanner
		reader.close();
		createSocket(LISTEN_PORT);
		//formrequestr forms the first tftp packet to be sent with the required options
		DatagramPacket packet = formRequest(WR, filename,mode);
		lastPacket = packet;

		try {
			socket.send(packet);

			//adds filepath to filename
			filename = encodeFilename(filename);
			if(WR == 1) {
				//starts readRequest method
				readRequest(filename);
			} else {
				//starts writeRequest method
				writeRequest(filename);
			}
		} catch (IOException e) {
			//handles file and filesystem errors (IOExceptions)
			String msg = e.getMessage();
			if(msg.equals("There is not enough space on the disk") || msg.equals("Not enough space") || msg.equals("No space left on device")){
				sendError(3,SEND_PORT);
			} else if(e.getClass().equals(AccessDeniedException.class)){
				sendError(2,SEND_PORT);
			} else if(e.getClass().equals(FileAlreadyExistsException.class)){
				sendError(6,SEND_PORT);
			}
			e.printStackTrace();
		}

	}

	//hgandles readrequests after the first packet is sent
	private static void readRequest(String filename) throws IOException
	{
		byte data[] = new byte[512];
		DatagramPacket received = new DatagramPacket(data, data.length);
		boolean keepReceiving = true;
		//makes a new File object pointing to the new file made
		File newFile = new File(filename);
		//creates it on the disk
		newFile.createNewFile();
		//to write to the file
		fileWriter = new FileOutputStream(filename);
		boolean cont;
		int counter = 1;

		//while the transfer is going on without any errors or anomalies
		while(keepReceiving) {
			data = new byte[512];
			arraycopy(received.getData(), received.getOffset(), data, 0, received.getLength());
			received.setData(data);

			try {
				//wait to receive from server
				socket.receive(received);
				System.out.println("RECEIVED: ");
				//prints detailed packet info if in verbose mode
				printPacket(received);
				if(counter == 1){
					//if its the first time receiving, set the correct ports and address to the ones fromthe current packet
					REC_PORT = received.getPort();
					address = received.getAddress();
				}
				cont = true;
				counter++;
				data = received.getData();
				arraycopy(received.getData(), received.getOffset(), data, 0, received.getLength());
				received.setData(data);
				//if the socket times out waiting
			} catch (SocketTimeoutException e) {
				if(counter > 1) {
					//if its the first time receiving, it sends the last packet (RRQ)
					socket.send(lastPacket);
				} else {
					//else it resends the last ACK
					sendACK(lastPacket);
				}
				cont = false;
			}
			//if somthing was received
			if(cont) {
				switch (validatePacket(received)) {
					case "DATA":
						if ((received.getPort() == REC_PORT) && (received.getAddress().equals(address))) {
							byte[] receivedBytes = unpackReadData(received);
							byte[] blockNumber = unpackBlockNumber(received);

							received.setPort(SEND_PORT);
							received.setAddress(address);
							//if the block numbers are correct
							if (Arrays.equals(blockNumber, nextBlock(block))) {
								System.out.println(received.getLength());
								if (received.getLength() < 512) {
									//if the array size is smaller than 512, it ends the transfer
									keepReceiving = false;
								}
								else if(received.getLength() > 512){
									//else if its larger, it sends an error wiuth code 4 and shuts down
									sendError(4,received.getPort()); //if greater than 512, its an invalid TFTP operation
									shutdown();
								}
								//increment block by 1
								block = nextBlock(block);
								//write into the file
								fileWriter.write(receivedBytes);
								//send an ACK
								sendACK(received);
							} else if (Arrays.equals(blockNumber, block)) {
								//if received the same data again, resend the ACK from last time
								sendACK(received);
							} else {
								//else send an error with code 4 `and shutdown
								sendError(4, received.getPort());
								shutdown();
							}
						} else {
							sendError(5, received.getPort());
							shutdown();
						}

						break;
					case "ERROR":
						keepReceiving = false;
						out.println("Server had an ERROR");
						shutdown();
						break;
					case "INVALID":
						keepReceiving = false;
						out.println("opcode error");
						sendError(4, received.getPort());
						shutdown();
						break;
					default:
						keepReceiving = false;
						out.println("There was an ERROR");
						shutdown();
						break;
				}
			}
		}
	}

	//This method sends an ACK with the current Block number
	private static void sendACK(DatagramPacket packet)
	{
		byte[] data = new byte[4];
		data[0] = 0;
		data[1] = 4;
		data[2] = block[0];
		data[3] = block[1];
		packet.setData(data);
		packet.setAddress(address);
		lastPacket = packet;

		try {
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//read packets block number
	private static byte[] unpackBlockNumber(DatagramPacket packet)
	{
		byte[] packetData = packet.getData();
		byte[] data = new byte[2];
		data[0] = packetData[2];
		data[1] = packetData[3];

		return data;
	}

	//read packets data
	private static byte[] unpackReadData(DatagramPacket packet)
	{
		byte[] packetData = packet.getData();
		byte[] data = new byte[packetData.length - 4];
		arraycopy(packetData, 4, data, 0, packetData.length - 4);

		return data;
	}

	//handles WRQs
	private static void writeRequest(String filename) throws IOException
	{
		byte data[] = new byte[512];
		DatagramPacket received = new DatagramPacket(data, data.length);
		byte[] fileBytes;
		fileBytes = createArray(filename);
		boolean keepSending = true;
		boolean cont;
		int counter = 1;

		while(keepSending) {
			try {
				socket.receive(received);
				cont = true;
				if(counter == 1) {
					REC_PORT = received.getPort();
					address = received.getAddress() ;
				}
			} catch(SocketTimeoutException e){
				if(lastPacket != null) {
					socket.send(lastPacket);
				}
				cont = false;
			}
			if(cont) {

				if (fileBytes.length < 508) {
					keepSending = false;
				}
				switch (validatePacket(received)) {

				    case "ACK":
						if ((received.getPort() == REC_PORT) && (received.getAddress().equals(address))) {
							byte[] blockNumber = unpackBlockNumber(received);
							if (Arrays.equals(blockNumber, block)) {
								block = nextBlock(block);
								fileBytes = sendData(fileBytes, received);
							} else {
								sendError(4, received.getPort());
							}
						} else {
							sendError(5, received.getPort());
							shutdown();
						}
						break;
					case "ERROR":
						keepSending = false;
						out.println("Server had an error");
						shutdown();
						break;
					case "INVALID":
						keepSending = false;
						out.println("opcode error");
						sendError(4, received.getPort());
						shutdown();
						break;
					default:
						out.println("DEFAULT");
						keepSending = false;
						out.println("There was an ERROR");
						shutdown();
						break;
				}
			}
			out.println(keepSending);
		}
	}

	//returns the block after the one given in the argument
	private static byte[] nextBlock(byte[] myBlock)
	{
		byte[] ret = new byte[2];
		if(myBlock[1] < 127){
			ret[1] = (byte) (myBlock[1] + 1);
		} else {
			ret[1] = 0;
			ret[0] = (byte) (myBlock[0] + 1);
		}

		return ret;
	}

	//sends data from a file and returns a changed array without the sent data
	private static byte[] sendData(byte[] fileBytes, DatagramPacket packet) throws IOException
	{
		byte[] data = new byte[512];
		data[0] = 0;
		data[1] = 3;
		data[2] = block[0];
		data[3] = block[1];
		int len;
		if(fileBytes.length < 508){
			len = fileBytes.length;
		} else {
			len = 508;
		}

		arraycopy(fileBytes, 0, data, 4, len);
		packet.setData(data, 0, data.length);
		lastPacket = packet;
		packet.setPort(SEND_PORT);
		packet.setAddress(address);
		printPacket(packet);
		socket.send(packet);
		byte[] changedFile;
		if(len >= 508){
			changedFile = new byte[fileBytes.length - len];

			arraycopy(fileBytes, 508, changedFile, 0, changedFile.length);
		} else {
			changedFile = new byte[1];
		}

		out.println(len + " " + fileBytes.length + " " + changedFile.length);

		return changedFile;
	}

	//returns type of packet
	private static String validatePacket(DatagramPacket packet)
	{
		byte[] data = packet.getData();
		if(data[0]==0){
			switch (data[1]) {
				case 1:
					return "RRQ";
				case 2:
					return "WRQ";
				case 3:
					return "DATA";
				case 4:
					return "ACK";
				case 5:
					return "ERROR";
				default:
					return "INVALID";
			}
		}else{
			return "INVALID";
		}
	}

	//Method to initialize socket
	private static void createSocket(int listenPort)
	{
		socket = null;
		//try/catch block for SocketException and UnknownHostException hat might arise from initializing the DatagramSocket and the InetAddress respectively
		try {
			socket = new DatagramSocket(listenPort, InetAddress.getByName("127.0.0.1"));
			socket.setSoTimeout(TIMEOUT);
		} catch (SocketException | UnknownHostException e) {
			e.printStackTrace();
		}
	}

	//Creates the DatagramPacket following the guidelines in the assignment document
	private static byte[] createArray(String filename) {
		out.println(filename);
		myInputStream = null;
		File file = new File(filename);
		try{
			myInputStream = new FileInputStream(file);
			out.println(myInputStream.available());
			//change catch back to filenotfound exception
		} catch (IOException e) {
			sendError(1,SEND_PORT);
			e.printStackTrace();
		}

		out.println(file.length());

		byte fileBytes[] = new byte[(int)file.length()];
		try{
			myInputStream.read(fileBytes);
		} catch (IOException e){
			e.printStackTrace();
		}

		//puts the final byte array into a new DatagramPacket and gives it the Address as well as the receiving port
		return fileBytes;
	}

	private static DatagramPacket formRequest(int WR, String filename, String mode)
	{
		byte[] wrBytes = new byte[2];
		wrBytes[0] = 0;
		byte[] filenameBytes = new byte[filename.length()];
		byte[] modeBytes = new byte[mode.length()];

		if(WR == 1 || WR == 2) {
			wrBytes[1] = (byte) WR;
		} else {
			wrBytes[0] = -1;
			wrBytes[1] = -1;
		}

		if(filename.length() > 0) {
			filenameBytes = filename.getBytes();
		}

		if(mode.length() > 0) {
			modeBytes = mode.getBytes();
		}

		//Adding the different parts of the packet into one byte array
		byte[] finalBytes = new byte[4 + filenameBytes.length + modeBytes.length];
		int j = 0;
		for(int i=0; i<finalBytes.length; i++) {
			if(i<2) {
				finalBytes[i] = wrBytes[i];
			} else if(i < 2+filenameBytes.length) {
				finalBytes[i] = filenameBytes[j];
				j++;
			} else if(i == 2+filenameBytes.length) {
				finalBytes[i] = 0;
				j = 0;
			} else if(i < 3+filenameBytes.length+modeBytes.length) {
				finalBytes[i] = modeBytes[j];
				j++;
			} else {
				finalBytes[i] = 0;
			}
		}

		//puts the final byte array into a new DatagramPacket and gives it the Address as well as the receiving port
		DatagramPacket packet = new DatagramPacket(finalBytes, finalBytes.length, new InetSocketAddress(address,SEND_PORT));
		printPacket(packet);

		return packet;
	}

	private static void sendError(int code, int port)
	{
		String errorMessage;
		byte[] data;
		switch(code){
			case 0: errorMessage =	"UNKNOWN ERROR";
				break;
			case 1: errorMessage =	"File Not Found";
				break;
			case 2: errorMessage =	"Access Violation";
				break;
			case 3: errorMessage =	"Disk Full/Allocation Exceeded";
				break;
			case 4: errorMessage =	"Illegal TFTP Operation";
				break;
			case 5: errorMessage =	"Unknown Transfer ID";
				break;
			case 6: errorMessage =	"File Already Exists";
				break;
			case 7: errorMessage =	"No Such User";
				break;
			default: errorMessage =	"Unknown Error";
				break;
		}
		byte[] message = errorMessage.getBytes();
		data = new byte[5 + message.length];
		data[0] = 0;
		data[1] = 5;
		data[2] = 0;
		data[3] = (byte) code;
		arraycopy(message, 0, data, 4, message.length + 4 - 4);
		data[data.length-1] = 0;
		DatagramPacket send = new DatagramPacket(data, data.length, new InetSocketAddress(address,port));
		printPacket(send);
		try {
			socket.send(send);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//method for printing DatagramPackets with a specific format, both in bytes and as a String, as well as the address and the port
	private static void printPacket(DatagramPacket p)
	{
		if (type.equals("V")){
			byte[] receivedBytes = p.getData();
			out.println("Data being sent/received in bytes: ");

			for(byte element : receivedBytes) {
				out.print(element);
			}

			out.println();
			String receivedString = new String(receivedBytes);
			out.println("Data being sent/received: " + receivedString);
			out.println("from/to address: " + p.getAddress());
			out.println("Port Number: " + p.getPort());
		}else {
			return;
		}
	}


	private static void shutdown() {
		socket.close();
		try {
			fileWriter.close();
			myInputStream.close();
			exit(0);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static String encodeFilename(String filename){
		return ClientPath + filename;
	}

}
