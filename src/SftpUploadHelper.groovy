/***
@Grab(group='commons-net', module='commons-net', version='3.3')
@Grab(group='com.jcraft', module='jsch', version='0.1.55')
*/

import org.apache.commons.net.ftp.FTPSClient
import com.jcraft.jsch.*;
import java.util.concurrent.*;
import java.util.*;
import java.util.logging.Logger

 class SftpUploadHelper{

		private static String ftpdomain  	      				= "localhost";
		private static int ftpport 		 	 				    = 2122;
		private static String ftpusername 		 			    = "";
		private static String ftppassword	   	  				= "";
		public  static String localFolder                       = "";
		public  static String remoteFolder                      = "";
		public  static int    threads                           = 10;
		public  static int    queueSize                         = 100;


		public static ArrayBlockingQueue<Runnable> threadQueue  = new ArrayBlockingQueue<Runnable>( queueSize );
        public static ThreadPoolExecutor threadPool      		= new ThreadPoolExecutor(1, threads, 2, TimeUnit.SECONDS, threadQueue,new ThreadPoolExecutor.CallerRunsPolicy());

		public static void startUpload(){

			ArrayList<String> filesInFolder =  new ArrayList<String>();

			File folder = new File(localFolder);
			if (folder.exists()){
				folder.eachFileRecurse { 
					def file = it;
					threadPool.execute {
					  (new SftpUploader (file.getAbsolutePath(),remoteFolder )).run();
					};
						}
					
				
			}
			threadPool.awaitTermination(60, TimeUnit.SECONDS);
			threadPool.shutdown();
		}

public static void main(String[] args){
		
        ftpdomain  	     = "localhost";
		ftpport   	     = 2122;
		ftpusername  	 = "sftpuser";
		ftppassword      = "SftpUser123!";
		localFolder      = "/mnt/c/Users/jgw51912/Documents/J/Jenkins/staging/output";
		//localFolder    = "/mnt/c/Users/jgw51912/Perforce/backup"
		remoteFolder     = "/opt/data";
		threads          = 20;
		queueSize        = 100;

		CliBuilder cliArgParser = new CliBuilder(usage:'SftpUploadHelper -h HOSTNAME -P PORT -u USERNAME -p PASSWORD -lf LOCAL_FOLDER -tf TARGET_FOLDER -t THREADS -qs THREAD_QUEUE_SIZE');
		cliArgParser.h(longOpt:'host', argName:'host', required: true,'The resolvable hostname or IP address of the sftp server');
		cliArgParser.P(longOpt:'port', argName:'port',required: true, 'The port that the sftp server listens on');
		cliArgParser.u(longOpt:'username',  argName:'userName', 'The username used to authenticate into the sftp server');
		cliArgParser.p(longOpt:'password', argName:'password', 'The password for the user specified');
		cliArgParser.t(longOpt:'threads',  argName:'threads', 'The the number of concurrent copy threads');
		cliArgParser.lf(longOpt:'localfolder',  argName:'localFolder',required: true, 'The local folder to be copied');
		cliArgParser.tf(longOpt:'targetfolder',  argName:'targetFolder', required: true,'The target folder on the sftp server');
		cliArgParser.qs(longOpt:'size',  argName:'queueSize', 'The size of the threadpool of concurrent threads');
 
        def options = cliArgParser.parse(args);

	   if (!options) {
			println('usage - SftpUploadHelper -h HOSTNAME -P PORT -u USERNAME -p PASSWORD -lf LOCAL_FOLDER -tf TARGET_FOLDER -t THREADS -qs THREAD_QUEUE_SIZE \n OR');
			println('usage- SftpUploadHelper -h HOSTNAME -P PORT -lf LOCAL_FOLDER -tf TARGET_FOLDER. If using the default SFTP server.');
			return
       }
		if (options.h) {
		  ftpdomain =options.h;
		}
		if (options.P) {
			ftpport=options.P;
		}
		if (options.u) {
			ftpusername=options.u;
		}
		if (options.p) {
			ftppassword=options.p;
		}
		if (options.t) {
			threads=options.t;
		}
		if (options.lf) {
			localFolder=options.fl;
		}
		if (options.tf) {
			localFolder=options.tf;
		}
		if (options.qs) {
			queueSize=options.qs;
		}
		startUpload();
		
	}
		
}

public class SftpUploader implements Runnable {
	   
	    private  String localFile    	  				= "";
		private  String remoteFolder       				= "";
		public   Logger log                             = Logger.getLogger("");
		public   int    retry                           = 0;
		public   final  int    MAX_RETRIES              = 5;
		
		public SftpUploader(String  localFile, String remoteFolder){
			
			this.localFile    = localFile;
			this.remoteFolder = remoteFolder;
		  
		}

		@Override
		public void run(){
                try{ 

					uploadFile(this.localFile, this.remoteFolder,this.retry);	

				}catch(Exception e){
                   if (this.retry< MAX_RETRIES){ 
						log.info("There was an error during the upload of ${this.localFile}. Retrying...");
						uploadFile(this.localFile, this.remoteFolder,(retry+1));
					}	

				}

		}
        public String createRemoteFolder(ChannelSftp sftp, String folder){

			StringBuilder remoteFolder = new StringBuilder();
            String[] folderLevels = folder.split('/');

			for (def subFolder  in folderLevels){ 
                remoteFolder.append(subFolder).append('/')
				try {
						sftp.cd( remoteFolder.toString() );
						//log.info("creating ${remoteFolder.toString()}")
					}
					catch ( SftpException e ) {

						sftp.mkdir( remoteFolder.toString() );
						// ((ChannelExec)sftp).setCommand("sudo mkdir -p "+ remoteFolder.toString());
						sftp.cd(remoteFolder.toString() );
					}

            }

			return remoteFolder.toString();

		}
		public void uploadFile(localfilelocation, remotefilelocation, retry){

			Session session = null;
			Channel channel = null;		
			try {
				String remoteFile = localfilelocation.replace(SftpUploadHelper.localFolder, remotefilelocation).replace('\\','/')
				//log.info("starting upload for "+localfilelocation+" to "+remoteFile);
				JSch ssh = new JSch();
						
				session = ssh.getSession(SftpUploadHelper.ftpusername, SftpUploadHelper.ftpdomain, SftpUploadHelper.ftpport);
				session.setConfig("StrictHostKeyChecking", "no"); //auto accept secure host
				session.setPassword(SftpUploadHelper.ftppassword);
				session.connect();
				//log.info("Connected to session");
						
				channel = session.openChannel("sftp");
				channel.connect();

				//log.info("Connected to channel")
						
				ChannelSftp sftp = (ChannelSftp) channel;
				if( new File(localfilelocation).isFile()){ 

					String remoteFolder = remoteFile.substring(0,remoteFile.lastIndexOf('/'));
					try {

						remoteFolder = this.createRemoteFolder(sftp,remoteFolder);

					} catch(Exception e){
							//log.info(remoteFolder+ " exists.");
							//log.info(e.printStackTrace())
					}
						sftp.put(new FileInputStream(localfilelocation), remoteFile, null, ChannelSftp.OVERWRITE);
						
						//log.info("File successfully uploaded FROM: " + localfilelocation + " TO: " + remoteFile);
				}else{
						
					try {

						remoteFolder = this.createRemoteFolder(sftp,remoteFile);
						// sftp.put(localfilelocation, remoteFile, null, ChannelSftp.OVERWRITE);

					} catch(Exception e){

						log.info("Error: "+e.getMessage());
						log.info(remoteFile+ " upload failed: "+ e.printStackTrace());
						if (this.retry< MAX_RETRIES){ 
							this.uploadFile(localfilelocation, remotefilelocation,(this.retry+1));
						}
					}

				}
			} catch (JSchException e) {
				
				//log.info("JSchException " + e.printStackTrace());
				//log.info("There was an error("+e.getMessage()+") during the upload of ${localfilelocation}. Retrying upload...");
			
					this.uploadFile(localfilelocation, remotefilelocation,(this.retry+1));
				
			} catch (SftpException e) {
				//log.info("SftpException " + e.printStackTrace());
				//log.info("There was an error("+e.getMessage()+") during the upload of ${localfilelocation}. Retrying upload...");

					this.uploadFile(localfilelocation, remotefilelocation,(this.retry+1));
				

			} finally {

				if (channel != null) {
					channel.disconnect();
					//log.info("Disconnected from channel");
				}
				if (session != null) {
					session.disconnect();
					//log.info("Disconnected from session");
				}
				//log.info("sftp upload process complete");
			}
		}
		
}