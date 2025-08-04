package com.leavebridge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Profile("prod") // 배포 환경일때만 적용
@Configuration
public class SshTunnelConfig {
	@Value("${ssh.host}")        private String sshHost;
	@Value("${ssh.port}")        private int sshPort;
	@Value("${ssh.user}")        private String sshUser;
	@Value("${ssh.privateKey}")  private String sshKey;
	@Value("${ssh.remoteHost}")  private String remoteHost;
	@Value("${ssh.remotePort}")  private int remotePort;
	@Value("${ssh.localPort}")   private int localPort;

	private Session session;

	@PostConstruct  // 의존성 주입 완료된 직후 초기화 콜백 메서드
	public void initSshTunnel() throws Exception {
		JSch jsch = new JSch();
		jsch.addIdentity(sshKey);                      // 키 파일 등록
		session = jsch.getSession(sshUser, sshHost, sshPort);  // 세션 객체 생성 (사용자명, 호스트, 포트 정보)
		session.setConfig("StrictHostKeyChecking", "no");  // 호스트키 검사(no 하면 자동으로 호스트키 신뢰)
		session.connect();                             // SSH 접속 수립 (TCP 연결, SSH 핸드쉐이크 및 인증)
		session.setPortForwardingL(localPort, remoteHost, remotePort); // localhost:localPort -> remoteHost:remotePort
		System.out.printf("SSH 터널 수립: localhost:%d → %s:%d%n",
			localPort, remoteHost, remotePort);
	}

	@PreDestroy // 애플리케이션 컨텍스트 종료 or 빈 제거 직전 호출되어 리소스 해체나 정리
	public void closeSshTunnel() {
		if (session != null && session.isConnected()) {
			session.disconnect();                      // 애플리케이션 종료 시 터널 해제
		}
	}
}
