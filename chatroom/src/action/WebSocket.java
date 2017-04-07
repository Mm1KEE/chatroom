package action;
/*
 * 模式(connect,sendMsg,broadcast,getUserList)/时间/发送人/接受人/消息
 * 
 * 
 */
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


@ServerEndpoint("/chat/{user}")
public class WebSocket {
	private String currentUser;
	private String[] recvMsg;
	private String mode="";
	private static Map<String, String> map = new HashMap<>(); 
	//静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
	private static int clientCount = 0;
	private Logger logger=LogManager.getLogger(WebSocket.class);

	//concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。若要实现服务端与单一客户端通信的话，可以使用Map来存放，其中Key可以为用户标识
	private static CopyOnWriteArraySet<WebSocket> webSocketSet = new CopyOnWriteArraySet<WebSocket>();

	//与某个客户端的连接会话，需要通过它来给客户端发送数据
	private Session session;

	//连接打开时执行
	@OnOpen
	public void onOpen(@PathParam("user") String user, Session session) {
		currentUser = user;	 
		this.session = session;
		webSocketSet.add(this);     //加入set中
		addOnlineCount();
		//logger.info(clientCount+"users online");
		add(session.getId(),user);
	}

	//收到消息时执行
	@OnMessage
	public void onMessage(String message, Session session) {
		recvMsg=message.split("\\|");
		mode=recvMsg[0];
		String targetName="";
		String targetId="";
		//System.out.println(message);
		switch(mode){
		case "connect":{
			logger.info("Session "+session.getId()+" connected as"+recvMsg[2]);
			break;
		}
		case "sendMsg":{
			targetName=recvMsg[3];
			targetId=getIdByName(targetName);
			logger.info("target name:"+targetName);
			for(WebSocket client: webSocketSet){
				if(client.session.getId().equals(targetId)){
					try {
						client.sendMessage("sendMsg"+"|"+getTime()+"|"+recvMsg[2]+"|"+targetName+"|"+recvMsg[4],targetId);
						logger.info("sendMsg"+"|"+getTime()+"|"+recvMsg[2]+"|"+targetName+"|"+recvMsg[4],targetId);
					} catch (IOException e) {
						e.printStackTrace();
						logger.error(e.getMessage());
					}
				}
			}	
			break;
		}
		case "broadcast":{
			logger.info("target name:"+targetName);
			for(WebSocket client: webSocketSet){
				try {
					client.sendMessage("broadcast"+"|"+getTime()+"|"+recvMsg[2]+"|"+"all"+"|"+recvMsg[4],targetId);
					logger.info("broadcast"+"|"+getTime()+"|"+recvMsg[2]+"|"+"all"+"|"+recvMsg[4],targetId);
				} catch (IOException e) {
					e.printStackTrace();
					logger.error(e.getMessage());
				}
			}
			break;
		}
		case "getUserList":{
			targetName=recvMsg[2];
			targetId=getIdByName(targetName);	
			for(WebSocket client: webSocketSet){
				if(client.session.getId().equals(targetId)){
					try {
						client.sendMessage("getUserList"+"|"+getTime()+"|"+recvMsg[2]+"|"+targetName+"|"+getUserList(),targetId);
						//logger.info("getUserList"+"|"+getTime()+"|"+recvMsg[2]+"|"+targetName+"|"+getUserList(),targetId);
					} catch (IOException e) {
						e.printStackTrace();
						logger.error(e.getMessage());
					}
				}
			}	
			break;
		}

		}



		/*logger.info(currentUser + "：" + recvMsg[2]);
		if(recvMsg.length==5){
			targetName=recvMsg[3];
			targetId=getIdByName(targetName);
			logger.info("target name:"+targetName);
			for(WebSocket client: webSocketSet){
				if(client.session.getId().equals(targetId)){
					try {
						client.sendMessage(recvMsg[2]+":"+recvMsg[4],targetId);
						logger.info(recvMsg[1]+"  "+recvMsg[2]+"→"+recvMsg[3]+":"+recvMsg[4]);
					} catch (IOException e) {
						e.printStackTrace();
						logger.error(e.getMessage());
					}
				}
			}
		}
		//return message;
		 */	}

	//连接关闭时执行
	@OnClose
	public void onClose(Session session, CloseReason closeReason) {
		System.out.println(String.format("Session %s closed because of %s", session.getId(), closeReason));
		webSocketSet.remove(this);  //从set中删除
		subOnlineCount();           //在线数减1    
		logger.info("Client "+session.getId()+" disconnected,"+ getOnlineCount()+" users online");
		map.remove(session.getId());
		if(closeReason.getReasonPhrase()!=null){
			logger.error("Session"+ session.getId()+ " closed because of "+closeReason);
		}
	}
	//连接错误时执行
	@OnError
	public void onError(Throwable t) {
		t.printStackTrace();
		logger.error(t.getMessage(),t);
		System.out.println(t.getMessage());
	}

	public void sendMessage(String message,String clientId) throws IOException{
		this.session.getBasicRemote().sendText(message);
	}

	public String getIdByName(String name){
		String id="";
		Set<String>kset=map.keySet();
		/*System.out.println("map size:"+map.size());
		System.out.println("key set size:"+kset.size());
		System.out.println("id    name");
		for(String s:kset){
			System.out.println(s+"-----"+map.get(s));
		}*/
		for(String n:kset){
			if(name.equals(map.get(n))){
				id=n;
				//logger.info("name:"+n+" id:"+id);
				break;
			}
		}
		return id;
	}

	public String getUserList(){
		String userList="";
		Set<String>kset=map.keySet();
		/*System.out.println("map size:"+map.size());
		System.out.println("key set size:"+kset.size());
		System.out.println("id    name");
		for(String s:kset){
			System.out.println(s+"-----"+map.get(s));
		}*/
		for(String n:kset){
			userList+=map.get(n);
			userList+=".";
		}
		return userList;
	}

	public String getTime() {
		String time = new Date().getHours() + ":" + new Date().getMinutes()
				+ ":" + new Date().getSeconds() + ":";
		return time;
	}

	public void add(String id,String name) {
		map.put(id, name);
	}
	public void remove(){
		map.remove(session.getId());
	}

	public static synchronized int getOnlineCount() {
		return clientCount;
	}

	public static synchronized void addOnlineCount() {
		WebSocket.clientCount++;
	}

	public static synchronized void subOnlineCount() {
		WebSocket.clientCount--;
	}
}
