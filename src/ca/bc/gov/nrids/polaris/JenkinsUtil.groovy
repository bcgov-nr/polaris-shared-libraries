package ca.bc.gov.nrids.polaris

import java.io.BufferedReader
import java.io.InputStreamReader

class JenkinsUtil implements Serializable {

  private final static def envLongToShort = [
    production: "prod",
    test: "test",
    development: "dev"
  ]

  /**
   * Get NR Broker compatible cause user id from build (currentBuild)
   * - Positional parameters
   * build          object  The global currentBuild object is expected
   * defaultUser    string  The default user id
   */
  static String getCauseUserId(build, defaultUser = "unknown") {
      def userIdCause = build.getBuildCauses('hudson.model.Cause$UserIdCause')
      final String nameFromUserIdCause = userIdCause != null && userIdCause[0] != null ? userIdCause[0].userId : null
      if (nameFromUserIdCause != null) {
        return nameFromUserIdCause.toLowerCase() + "@azureidir"
      } else {
        return defaultUser ? defaultUser : 'unknown'
      }
  }

  /**
   * Converts a standard long environment name into its short version
   * - Positional parameters
   * env          string  The environment name to convert
   */
  static String convertLongEnvToShort(env) {
    return JenkinsUtil.envLongToShort[env]
  }

  static void putFile(username, password, apiURL, filePath) {
    try {
      def requestBody = new File(filePath)
      def encodeBody = URLEncoder.encode(requestBody, "UTF-8")
      def url = new URL(apiURL)
      def connection = url.openConnection()

      // Basic Authentication
      String auth = "${username}:${password}"
      String encodedAuth = Base64.getEncoder().encodeToString(auth.bytes)
      String authHeader = "Basic ${encodedAuth}"
      connection.setRequestProperty("Authorization", authHeader)
      
      connection.setRequestMethod("POST")
      connection.setRequestProperty("Content-Type", "application/octet-stream")
      connection.setDoOutput(true)
    
      OutputStream os = connection.getOutputStream()
      byte[] input = encodeBody.getBytes("utf-8")
      os.write(input, 0, input.length)    

      def responseCode = connection.getResponseCode()

      println "Response Code: $responseCode"

      if (responseCode == HttpURLConnection.HTTP_OK) {
          BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))
          StringBuilder response = new StringBuilder()
          String responseLine
          while ((responseLine = br.readLine()) != null) {
              response.append(responseLine.trim())
          }
          println "Response Data: $response"
      } else {
          println "Error: ${connection.getResponseMessage()}"
      }
    } catch (Exception e) {
      println "Error Occurs: ${e.message}"
      throw e
    }
  }

  String runShellCommand(String command) {
    try {
        ProcessBuilder processBuilder = new ProcessBuilder(command.split("\\s+"))
        Process process = processBuilder.start()

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.inputStream))
        StringBuilder output = new StringBuilder()

        String line
        while ((line = reader.readLine()) != null) {
            output.append(line).append('\n')
        }

        int exitCode = process.waitFor()
        if (exitCode == 0) {
            return output.toString().trim()
        } else {
            println("Error running command: $command")
            return null
        }
    } catch (Exception ex) {
        println("Exception running command: $command - ${ex.message}")
        return null
    }
  }

  String runSha256Command(String filename) {
    try {
      String command = "sha256sum $filename"
      String sha256sumOutput = runShellCommand(command)
      return sha256sumOutput
    } catch (Exception ex) {
        println("Exception running command: $filename - ${ex.message}")
        return null
    }

  }
}
