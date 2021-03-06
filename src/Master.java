import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


/*
 * Team Members: Hardik Trivedi (hpt150030)
 * 				  Roshan Ravikumar (rxr151330)
 * 				  Sapan Gandhi (sdg150130) 
 */


public class Master 
{
	//Master process ID
	private int masterProcessId;

	//processes write to this to indicate that they are ready for the next round.
	private BlockingQueue<Message> masterQueue;

	//number of processes in the ring
	private int numberOfProcesses;

	boolean completed = false; 

	/*
	 * this collection contains an array of BlockingQueues
	 * master writes to processes' respective queue with a start of new round message (message type = N)
	 */
	private ArrayList<BlockingQueue<Message>> arrQueue = new ArrayList<BlockingQueue<Message>>();
	private ArrayList<BlockingQueue<Message>> arrRoundQueue = new ArrayList<BlockingQueue<Message>>();

	public Master(int masterProcessId, int[] id) //id array contains the unique id of the processes
	{
		this.masterProcessId = masterProcessId;

		//number of processes
		numberOfProcesses = id.length;

		//ready for next round queue
		masterQueue = new ArrayBlockingQueue<>(numberOfProcesses);

		Message readyMsg;
		BlockingQueue<Message> processQueue, roundQueue;

		for(int i = 0; i < numberOfProcesses; i++)
		{
			readyMsg = new Message(null, 'R', Integer.MIN_VALUE, Integer.MIN_VALUE, null, Integer.MIN_VALUE);
			masterQueue.add(readyMsg);

			/*
			 * this queue has capacity 2 as at any point of time if will have a maximum of 2 messages in it.
			 * One from left neighbor and one from right neighbor
			 * While processing that message it will remove it from the queue
			 * Also messages from the master requesting to start next round will land here
			 */
			processQueue = new ArrayBlockingQueue<>(6);
			roundQueue = new ArrayBlockingQueue<>(6);
			arrQueue.add(processQueue);
			arrRoundQueue.add(roundQueue);
		}
	}

	/*
	 * if all the values in the masterQueue have the message type value as R, return true
	 * else return false
	 */
	public boolean validateNewRoundStart()
	{
		/*
		 * if number of message in the queue is less than nuumberOfProcesses then return false
		 */

		int count = 0;

		if(masterQueue.size() < numberOfProcesses)
		{
			return false;
		}

		Message msg;

		for(int i = 0; i < numberOfProcesses; i++)
		{
			try 
			{
				//remove the message from the queue and check its type
				msg = masterQueue.take();
				if(msg.getType() != 'R' && msg.getType() != 'L')
				{
					return false;
				}

				if(msg.getType() == 'L')
				{
					count++;

					if(count == numberOfProcesses)
					{
						completed = true;
						return false;
					}
				}
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}

		return true;
	}

	/*
	 * Send to all the processes in their respective message queue that they should be starting the next round
	 */
	public void startNextRound()
	{
		Iterator<BlockingQueue<Message>> it = arrRoundQueue.iterator();
		BlockingQueue<Message> blkq;
		Message msg;

		while(it.hasNext())
		{
			blkq = it.next();
			msg = new Message(null, 'N', Integer.MIN_VALUE, Integer.MIN_VALUE, null, Integer.MIN_VALUE);;
			blkq.add(msg);
		}
	}

	public BlockingQueue<Message> getMasterQueue()
	{
		return masterQueue;
	}

	public ArrayList<BlockingQueue<Message>> getProcessMasterQueue()
	{
		return arrQueue;
	}

	public ArrayList<BlockingQueue<Message>> getRoundQueue()
	{
		return arrRoundQueue;
	}

	public boolean isCompleted()
	{
		return completed;
	}

	public static void main(String[] args)
	{
		int n = 0;
		String ids[] = null;
		String neighbors[] = null;
		
		try{
			BufferedReader reader  = new BufferedReader(new FileReader(args[0]));
			n = Integer.parseInt(reader.readLine().trim());
			ids = new String[n];
			ids = reader.readLine().trim().replaceAll(" +", " ").split(" ");
			int i = 0;
			neighbors = new String[n];
			String temp = null;
			for (int j = 0; j < n; j++) {
				temp = reader.readLine().trim().replaceAll(" +", " ");
				neighbors[j]=temp;
			}
		}catch(FileNotFoundException fnfe) {
			System.out.println("Connectivity file not found. Please check the file name and try again");
			System.exit(-1);
		} catch (NumberFormatException nfe) {
			System.out.println("Please check the input file. NumberFormatException found");
			System.exit(-1);
		} catch (IOException e) {
			System.out.println("IOException while reading the file. Please check the input file and try again");
			System.exit(-1);
		}
		
		//accept input from input.dat
		int masterProcessId = 0;
		
		//getting n
		int[] id = new int[n];
		
		//constructing id
		for(int i = 0; i < n; i++)
		{
			id[i] = Integer.parseInt(ids[i]);
		}
		
		//creating the master process. Master thread is the main thread
		Master masterProcess = new Master(masterProcessId, id);

		//creating other threads for HS algorithm simulation
		Process[] processes = new Process[n];

		for(int i = 0; i < n; i++)
		{
			processes[i] = new Process(id[i]);
			processes[i].setQRound(masterProcess.getRoundQueue().get(i));
		}
		
		Link link;
		for(int i = 1; i < neighbors.length; i++)
		{
			String neighbor = neighbors[i];
			String[] tempNeighbor = neighbor.split(" ");
			for(int j = 0; j < i; j++)
			{
				if(tempNeighbor[j].equals("1"))
				{
					link = new Link(processes[i], processes[j]);
					System.out.println(processes[i].getId()+"--->"+processes[j].getId());
					processes[i].addLink(link);
					processes[j].addLink(link);
				}
			}
		}
		
		for(int i = 0; i < n; i++)
		{
			processes[i].initialize();
		}
		
		//TODO
		//add ur id to outlist

		//starting all the threads
		Thread[] t = new Thread[n];
		for(int i = 0; i < n; i++)
		{
			//reference for the queue to which processes will write ready for next round
			processes[i].setQMaster(masterProcess.getMasterQueue());
			t[i] = new Thread(processes[i]);
			t[i].start();
		}

		/*
		 * keep looping till HS algorithm is completed
		 */
		while(!masterProcess.isCompleted())
		{
			if(masterProcess.validateNewRoundStart())
			{
				masterProcess.startNextRound();
			}
		}

		for(int i = 0; i < n; i++)
		{
			t[i].interrupt();
		}

		//waiting till all child thread complete
		for(int i = 0; i < t.length; i++)
		{
			try {
				t[i].join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		System.out.println("Completed!!");
	}
}
