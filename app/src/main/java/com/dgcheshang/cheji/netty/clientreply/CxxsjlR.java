package com.dgcheshang.cheji.netty.clientreply;


/**
 * 查询参数回复
 * @author Administrator
 *
 */
public class CxxsjlR implements java.io.Serializable{

	private static final long serialVersionUID = 1L;
	private int jg;//执行结果
	public int getJg() {
		return jg;
	}
	public void setJg(int jg) {
		this.jg = jg;
	}

	public byte[] getCxxsjlBytes(){
		byte[] bs=new byte[1];
		bs[0]= (byte) jg;
		return bs;
	}
}
