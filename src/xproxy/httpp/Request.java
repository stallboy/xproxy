package xproxy.httpp;


public final class Request extends Message {
	
	private String method;
	private String url;
	private String version;
	
	public String getRequestLine(){
		return startline;
	}
	public String getMethod(){
		return method;
	}
	public String getRequestUrl(){
		return url;
	}
	public String getVersion(){
		return version;
	}
	
	@Override
	public void setStartLine(String line){
		startline = line;
		String[] s = startline.split(" ");
		method = s[0];
		url = s[1];
		version= s[2];
	}
	
	public static Request fromMessage(Message msg){
		Request req = new Request();
		req.setStartLine(msg.startline);
		req.headers = msg.headers;
		req.body = msg.getBody();
		return req;
	}


	@Override
	protected BodyType findBodyType() {
		String tf = headers.get(TRANSFER_ENCODING);
		String cl = headers.get(CONTENT_LENGTH);
		
		if ( tf != null && !tf.equals("identity") )
			return BodyType.CHUNKED;
		else if ( cl != null && !cl.equals("0") )
			return BodyType.BY_LENGTH;
		
		return BodyType.EMPTY;
	}
	
}
