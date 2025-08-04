package com.leavebridge.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceLoader;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration("sshTunnelConfig")
@Profile("prod")
public class SshTunnelConfig {

	@Value("${ssh.host}")        private String sshHost;
	@Value("${ssh.port}")        private int sshPort;
	@Value("${ssh.user}")        private String sshUser;
	@Value("${ssh.privateKey}")  private String sshKey;

	@Value("${ssh.remoteHost}")  private String remoteHost;
	@Value("${ssh.remotePort}")  private int remotePort;
	@Value("${ssh.localPort}")   private int localPort;

	private ClientSession session;

	@PostConstruct
	public void initSshTunnel() throws Exception {
		SshClient client = SshClient.setUpDefaultClient();  // 기본 클라이언트 생성
		client.start();                                      // 클라이언트 시작

		// PEM 키 파싱용 로더 생성
		KeyPairResourceLoader loader = SecurityUtils.getKeyPairResourceParser();
		Path keyPath = Paths.get(sshKey);

		// loader.loadKeyPairs : PEM, OpenSSH, RFC4716 등 다양한 키 포맷을 모두 파싱할 수 있도록 설계
		// 하나의 파일에 여러 키 쌍이 포함되어 있거나 서로 다른 알고리즘의 키 연속 저장된 경우 있어 모든 키 순회하기 위한 Collection
		// 이후 반환 컬렉션 중 필요한 키 하나를 선택하여 공개키 인증에 사용
		/**
		 * 첫번째 인자 : 선택적 세션 컨텍스트 또는 리소스 식별자
		   - 키 파싱 시 추가 정보(호스트 식별 등) 제공
		   - 파일 기반 파싱 null로 기본 컨텍스트 사용
		 * 두번째 인자 : 실제 키 파일 경로, 이 객체가 가리키는 파일을 읽어 내부 하나 이상의 개인키, 공개키 쌍 파싱
		 * 세번째 인자 : 패스프레이즈 공급자, 암호화된 키 파일의 복호화를 위해 필요한 패스프레이즈
		   - 패스 프레이즈 없음(빈문자), **암호화 되지 않은 키를 다룰 때 사용**
		   - 만일 키 파일이 `passphrase-protected` 상태라면 실제 패스프레이즈를 입력해야 함.
		 */
		Collection<KeyPair> keys = loader.loadKeyPairs(null, keyPath, FilePasswordProvider.of(""));

		// SSH 세션 생성 및 연결
		session = client.connect(sshUser, sshHost, sshPort)
			.verify(10, TimeUnit.SECONDS)
			.getSession();
		// 공개키 인증
		session.addPublicKeyIdentity(keys.iterator().next());
		session.auth().verify(10, TimeUnit.SECONDS);

		SshdSocketAddress localAddr  = new SshdSocketAddress("localhost", localPort);
		SshdSocketAddress remoteAddr = new SshdSocketAddress(remoteHost, remotePort);
		session.startLocalPortForwarding(localAddr, remoteAddr);  // 포워딩 설정
	}

	@PreDestroy
	public void closeSshTunnel() throws Exception {
		if (session != null && session.isOpen()) {
			session.close();  // 터널 해제
		}
	}
}
