/*
 * Copyright 2021 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package misk.aws2.dynamodb.testing

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Ports
import misk.containers.Composer
import misk.containers.Container
import misk.logging.getLogger
import misk.testing.ExternalDependency
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException

/**
 * A test DynamoDb Local service. Tests can connect to the service at 127.0.0.1:<random_port>.
 * Use endpointConfiguration to get the service endpoint address:
 *  DockerDynamoDb.endpointConfiguration.serviceEndpoint
 */
object DockerDynamoDb : ExternalDependency {
  private val logger = getLogger<DockerDynamoDb>()

  private val pid = ProcessHandle.current().pid()

  internal val localDynamoDb = LocalDynamoDb()

  override val id = "dynamodb-local-$pid"

  private val url = localDynamoDb.url

  val awsCredentialsProvider = localDynamoDb.awsCredentialsProvider

  private val composer = Composer(
    "e-$id",
    Container {
      // DynamoDB Local listens on port 8000 by default.
      val exposedClientPort = ExposedPort.tcp(8000)
      val portBindings =
        Ports().apply { bind(exposedClientPort, Ports.Binding.bindPort(url.port)) }
      withImage("amazon/dynamodb-local")
        .withName(id)
        .withExposedPorts(exposedClientPort)
        .withCmd("-jar", "DynamoDBLocal.jar", "-sharedDb")
        .withPortBindings(portBindings)
    }
  )

  override fun beforeEach() {
    // noop
  }

  override fun afterEach() {
    // noop
  }

  override fun startup() {
    composer.start()
    val client = connect()
    while (true) {
      try {
        client.deleteTable(DeleteTableRequest.builder().tableName("not a table").build())
      } catch (e: Exception) {
        if (e is DynamoDbException) {
          break
        }
        logger.info { "DynamoDb is not available yet" }
        Thread.sleep(100)
      }
    }
    client.close()
    logger.info { "DynamoDb is available" }
  }

  override fun shutdown() {
    composer.stop()
  }

  fun connect(): DynamoDbClient = localDynamoDb.connect()
}

fun main(args: Array<String>) {
  DockerDynamoDb.startup()
}
