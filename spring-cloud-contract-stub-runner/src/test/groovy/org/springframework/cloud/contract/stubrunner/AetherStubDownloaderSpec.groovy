package org.springframework.cloud.contract.stubrunner

import io.specto.hoverfly.junit.HoverflyRule
import org.eclipse.aether.RepositorySystemSession
import org.junit.Rule
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.util.ResourceUtils
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

class AetherStubDownloaderSpec extends Specification {

	@Rule
	HoverflyRule hoverflyRule = HoverflyRule.inSimulationMode("simulation.json")

	def 'Should throw an exception when artifact not found'() {
		given:
			StubRunnerOptions stubRunnerOptions = new StubRunnerOptionsBuilder()
					.withStubsMode(StubRunnerProperties.StubsMode.LOCAL)
					.build()

			AetherStubDownloader aetherStubDownloader = new AetherStubDownloader(stubRunnerOptions)

		when:
			def jar = aetherStubDownloader.downloadAndUnpackStubJar(new StubConfiguration("non.existing.group", "missing-artifact-id", "1.0-SNAPSHOT"))

		then:
			IllegalStateException e = thrown(IllegalStateException)
			e.message.contains("Exception occurred while trying to download a stub for group")
	}

	def 'Should throw an exception when a jar is in local m2 and not in remote repo'() {
		given:
			StubRunnerOptions stubRunnerOptions = new StubRunnerOptionsBuilder()
					.withStubsMode(StubRunnerProperties.StubsMode.REMOTE)
					.withStubRepositoryRoot("https://test.jfrog.io/test/libs-snapshot-local")
					.build()

			AetherStubDownloader aetherStubDownloader = new AetherStubDownloader(stubRunnerOptions)

		when:
			def jar = aetherStubDownloader.downloadAndUnpackStubJar(new StubConfiguration("org.springframework.cloud", "spring-cloud-contract-spec", "+", ""))

		then:
			IllegalStateException e = thrown(IllegalStateException)
			e.message.contains("The artifact was found in the local repository but you have explicitly stated that it should be downloaded from a remote one")
	}

	@RestoreSystemProperties
	def 'Should not throw an exception when a jar is in local m2 and not in remote repo and system property disabled snapshot check'() {
		given:
			StubRunnerOptions stubRunnerOptions = new StubRunnerOptionsBuilder()
					.withStubsMode(StubRunnerProperties.StubsMode.REMOTE)
					.withStubRepositoryRoot("https://test.jfrog.io/test/libs-snapshot-local")
					.build()
			System.properties.setProperty("stubrunner.snapshot-check-skip", "true")

		and:
			AetherStubDownloader aetherStubDownloader = new AetherStubDownloader(stubRunnerOptions)

		when:
			def jar = aetherStubDownloader.downloadAndUnpackStubJar(new StubConfiguration("org.springframework.cloud", "spring-cloud-contract-spec", "+", ""))

		then:
			jar != null
	}

	@RestoreSystemProperties
	def 'Should throw an exception when a jar is in local m2 and not in remote repo and system property disabled takes precedence over env'() {
		given:
			StubRunnerOptions stubRunnerOptions = new StubRunnerOptionsBuilder()
					.withStubsMode(StubRunnerProperties.StubsMode.REMOTE)
					.withStubRepositoryRoot("https://test.jfrog.io/test/libs-snapshot-local")
					.build()
			System.properties.setProperty("stubrunner.snapshot-check-skip", "false")

		and:
			StubRunnerPropertyUtils.FETCHER = new PropertyFetcher() {
				@Override
				String systemProp(String prop) {
					return super.systemProp(prop)
				}

				@Override
				String envVar(String prop) {
					return "true"
				}
			}
			AetherStubDownloader aetherStubDownloader = new AetherStubDownloader(stubRunnerOptions)

		when:
			def jar = aetherStubDownloader.downloadAndUnpackStubJar(new StubConfiguration("org.springframework.cloud", "spring-cloud-contract-spec", "+", ""))

		then:
			IllegalStateException e = thrown(IllegalStateException)
			e.message.contains("The artifact was found in the local repository but you have explicitly stated that it should be downloaded from a remote one")
	}

	def 'Should not throw an exception when a jar is in local m2 and not in remote repo and env property disabled snapshot check'() {
		given:
			StubRunnerOptions stubRunnerOptions = new StubRunnerOptionsBuilder()
					.withStubsMode(StubRunnerProperties.StubsMode.REMOTE)
					.withStubRepositoryRoot("https://test.jfrog.io/test/libs-snapshot-local")
					.build()

		and:
			StubRunnerPropertyUtils.FETCHER = new PropertyFetcher() {
				@Override
				String systemProp(String prop) {
					return super.systemProp(prop)
				}

				@Override
				String envVar(String prop) {
					return "true"
				}
			}
			AetherStubDownloader aetherStubDownloader = new AetherStubDownloader(stubRunnerOptions)

		when:
			def jar = aetherStubDownloader.downloadAndUnpackStubJar(new StubConfiguration("org.springframework.cloud", "spring-cloud-contract-spec", "+", ""))

		then:
			jar != null

		cleanup:
			StubRunnerPropertyUtils.FETCHER = new PropertyFetcher()
	}

	def 'Should not throw an exception when a jar is in local m2 and not in remote repo and option disabled snapshot check'() {
		given:
			StubRunnerOptions stubRunnerOptions = new StubRunnerOptionsBuilder()
					.withStubsMode(StubRunnerProperties.StubsMode.REMOTE)
					.withStubRepositoryRoot("https://test.jfrog.io/test/libs-snapshot-local")
					.withSnapshotCheckSkip(true)
					.build()

			AetherStubDownloader aetherStubDownloader = new AetherStubDownloader(stubRunnerOptions)

		when:
			def jar = aetherStubDownloader.downloadAndUnpackStubJar(new StubConfiguration("org.springframework.cloud", "spring-cloud-contract-spec", "+", ""))

		then:
			jar != null
	}

	@RestoreSystemProperties
	def 'Should use local repository from settings.xml'() {
		given:
			File tempSettings = File.createTempFile("settings", ".xml")
			def m2repoFolder = 'm2repo' + File.separator + 'repository'
			tempSettings.text = '<settings><localRepository>' +
					ResourceUtils.getFile(ResourceUtils.CLASSPATH_URL_PREFIX + m2repoFolder).getAbsolutePath() + '</localRepository></settings>'
			System.setProperty("org.apache.maven.user-settings", tempSettings.getAbsolutePath())
			RepositorySystemSession repositorySystemSession =
					AetherFactories.newSession(AetherFactories.newRepositorySystem(), true);

		and:
			StubRunnerOptions stubRunnerOptions = new StubRunnerOptionsBuilder()
					.withStubsMode(StubRunnerProperties.StubsMode.LOCAL)
					.build()
			AetherStubDownloader aetherStubDownloader = new AetherStubDownloader(stubRunnerOptions)

		when:
			def jar = aetherStubDownloader.downloadAndUnpackStubJar(
					new StubConfiguration("org.springframework.cloud.contract.verifier.stubs",
							"bootService", "0.0.1-SNAPSHOT"))

		then:
			jar != null
			repositorySystemSession.getLocalRepository().getBasedir().getAbsolutePath().endsWith(m2repoFolder)
	}
}
