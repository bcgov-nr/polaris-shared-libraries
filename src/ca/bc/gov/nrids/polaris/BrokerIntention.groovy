package ca.bc.gov.nrids.polaris

class BrokerIntention implements Serializable {
  Object intention
  Object openResponse
  static final String BROKER_BASE_URL = "https://broker.io.nrs.gov.bc.ca/v1/"
  static final String HEADER_BROKER_TOKEN = "X-Broker-Token"

  BrokerIntention(Object intention) {
    this.intention = intention
  }

  /**
   * Reads the intention json from the path and returns a new BrokerIntention.
   * - Positional parameters
   * path   String  The path to the file to read
   */
  static BrokerIntention fromFile(String path) {
    def jsonSlurper = new groovy.json.JsonSlurperClassic()
    return new BrokerIntention(jsonSlurper.parseText(new File(path).text))
  }

  // Convenience getter/setter for user id & name, event url and event provider
  def getUserId() {
    return intention.user.id
  }
  def setUserId(user) {
    intention.user.id = (String) user
  }

  def getUserName() {
    return intention.user.name
  }
  def setUserName(user) {
    intention.user.name = (String) user
  }

  def getEventUrl() {
    return intention.event.url
  }
  def setEventUrl(url) {
    intention.event.url = (String) url
  }

  def getEventProvider() {
    return intention.event.provider
  }
  def setEventProvider(provider) {
    intention.event.provider = (String) provider
  }

  /**
   * Chainable event details setter
   * - Named parameters
   * userName:                    string Sets username
   * url:                         string Sets event url
   * provider:                    string Sets event provider
   * serviceName                  string Sets service name
   * serviceProject               string Sets service project
   * environment:                 string Sets all action environments to this value
   * packageInstallationVersion:  string Sets all "package-installation" actions package.version to this value
   */
  public BrokerIntention setEventDetails(Map args) {
    if (args.userName) {
      this.setUserName(args.userName);
    }
    if (args.url) {
      this.setEventUrl(args.url);
    }
    if (args.provider) {
      this.setEventProvider(args.provider);
    }
    if (args.serviceName) {
      for (action in this.intention.actions) {
        action.service.name = args.serviceName
      }
    }
    if (args.serviceProject) {
      for (action in this.intention.actions) {
        action.service.project = args.serviceProject
      }
    }
    if (args.environment) {
      for (action in this.intention.actions) {
        action.service.environment = args.environment
      }
    }
    this.updatePackageForAction("package-build", "buildGuid", args.packageBuildBuildGuid)
    this.updatePackageForAction("package-build", "buildNumber", args.packageBuildBuildNumber)
    this.updatePackageForAction("package-build", "buildVersion", args.packageBuildBuildVersion)
    this.updatePackageForAction("package-build", "description", args.packageBuildDescription)
    this.updatePackageForAction("package-build", "name", args.packageBuildName)
    this.updatePackageForAction("package-build", "type", args.packageBuildType)
    this.updatePackageForAction("package-build", "version", args.packageBuildVersion)
    // Update installation package
    this.updatePackageForAction("package-installation", "name", args.packageInstallationName)
    this.updatePackageForAction("package-installation", "version", args.packageInstallationVersion)
    // Update installation source
    if (args.packageInstallationSourceIntention) {
      for (action in this.intention.actions) {
        if (action.action == "package-installation") {
          action.source.intention = args.packageInstallationSourceIntention
        }
      }
    }

    return this
  }

  /**
   * Open the intention
   * - Positional parameters
   * authToken   String   The JWT to send to authenticate this request
   * - Named parameters
   * quickstart: boolean  If an intention has a single action, setting this true will start the action.
   * ttl:        Number   The time to live (ttl) for the intention.
   *                      Long running processes may need to set this higher than the default.
   */
  public boolean open(Map args, String authToken) {
    def jsonSlurper = new groovy.json.JsonSlurperClassic()

    if (!authToken) {
      throw new IllegalArgumentException()
    }

    // POST open
    def params = [:]
    if (args.ttl) {
      params["ttl"] = args.ttl
    }
    if (args.quickstart) {
      params["quickstart"] = "true"
    }
    def paramStr = params.collect({ it }).join('&')
    def post = new URL(this.BROKER_BASE_URL + "intention/open?" + paramStr).openConnection()
    def message = groovy.json.JsonOutput.toJson(intention)
    post.setRequestMethod("POST")
    post.setDoOutput(true)
    post.setRequestProperty("Content-Type", "application/json")
    post.setRequestProperty("Authorization", "Bearer " + authToken)
    post.getOutputStream().write(message.getBytes("UTF-8"))
    def postRC = post.getResponseCode()
    if (this.isResponseSuccess(postRC)) {
      this.openResponse = jsonSlurper.parseText(post.getInputStream().getText())
      return true
    }
    def errorResponseBody = post.getErrorStream()?.getText() ?: "No error response body"
    def errorMessage = "Failed to open intention. Response code: $postRC Response body: $errorResponseBody"
    throw new IllegalStateException(errorMessage)
  }
  def open(String authToken) {
    this.open([:], authToken)
  }

  /**
   * Start the action
   * - Positional parameters
   * action   String  The action id from the intention
   */
  public void startAction(String action) {
    if (!this.openResponse) {
      throw new IllegalStateException()
    }
    if (!action || !this.openResponse.actions[action]) {
      throw new IllegalArgumentException()
    }
    this.actionLifecycleLog(this.openResponse.actions[action].token, "start")
  }

  /**
   * End the action
   * - Positional parameters
   * action   String  The action id from the intention
   */
  public void endAction(String action) {
    if (!this.openResponse) {
      throw new IllegalStateException()
    }
    if (!action || !this.openResponse.actions[action]) {
      throw new IllegalArgumentException()
    }
    this.actionLifecycleLog(this.openResponse.actions[action].token, "end")
  }

  public boolean actionLifecycleLog(String token, String type) {
    def post = new URL(this.BROKER_BASE_URL + "intention/action/" + type).openConnection()
    post.setRequestMethod("POST")
    post.setRequestProperty("Content-Type", "application/json")
    post.setRequestProperty(HEADER_BROKER_TOKEN, token)
    return this.isResponseSuccess(post.getResponseCode())
  }

  public boolean registerActionArtifact(String action, String message) {
    if (!this.openResponse) {
      throw new IllegalStateException()
    }
    if (!action || !this.openResponse.actions[action]) {
      throw new IllegalArgumentException()
    }

    def post = new URL(this.BROKER_BASE_URL + "intention/action/artifact").openConnection()
    post.setRequestMethod("POST")
    post.setDoOutput(true)
    post.setRequestProperty("Content-Type", "application/json")
    post.setRequestProperty(HEADER_BROKER_TOKEN, this.openResponse.actions[action].token)
    post.getOutputStream().write(message.getBytes("UTF-8"))
    return this.isResponseSuccess(post.getResponseCode())
  }

  public boolean patchAction(String action, String message) {
    if (!this.openResponse) {
      throw new IllegalStateException()
    }
    if (!action || !this.openResponse.actions[action]) {
      throw new IllegalArgumentException()
    }

    def post = new URL(this.BROKER_BASE_URL + "intention/action/patch").openConnection()
    post.setRequestMethod("POST")
    post.setDoOutput(true)
    post.setRequestProperty("Content-Type", "application/json")
    post.setRequestProperty(HEADER_BROKER_TOKEN, this.openResponse.actions[action].token)
    post.getOutputStream().write(message.getBytes("UTF-8"))
    def postRC = post.getResponseCode()
    if (!this.isResponseSuccess(postRC)) {
      def errorResponseBody = post.getErrorStream()?.getText() ?: "No error response body"
      def errorMessage = "Failed to patch. Response code: $postRC Response body: $errorResponseBody"

      throw new IllegalStateException(errorMessage)
    }
    return true
  }

  /**
   * Close the intention
   * - Positional parameters
   * successArg   boolean  Set true if all actions were a success
   */
  public String close(boolean successArg) {
    // Ignore close if there is no open response
    if (!this.openResponse) {
      throw new IllegalStateException("Intention was never opened")
    }
    // POST close
    def jsonSlurper = new groovy.json.JsonSlurperClassic()
    def post = new URL(
      this.BROKER_BASE_URL + "intention/close?outcome=" +
      (successArg ? "success": "failure")).openConnection()
    post.setRequestMethod("POST")
    post.setRequestProperty("Content-Type", "application/json")
    post.setRequestProperty(HEADER_BROKER_TOKEN, this.openResponse.token)
    def resp = jsonSlurper.parseText(post.getInputStream().getText())
    return resp.audit
  }

  /**
   * Provision a secret id that can be used by the service
   * to login as the service to vault.
   * - Positional parameters
   * action   String  The action id from the intention
   * roleId   String  The service role id
   *
   * Note: The return value is wrapper token that should be
   * unwrapped only on the server using it.
   *
   * Unwrap example: vault unwrap -field=secret_id <token>
   */
  public String provisionSecretId(action, roleId = null) {
    if (!action) {
      throw new IllegalArgumentException()
    }
    if (!this.openResponse) {
      throw new IllegalStateException()
    }
    def actionToken = this.openResponse?.actions[action].token
    if (!actionToken) {
      throw new IllegalArgumentException()
    }
    def jsonSlurper = new groovy.json.JsonSlurperClassic()
    def post = new URL(this.BROKER_BASE_URL + "provision/approle/secret-id").openConnection()
    post.setRequestMethod("POST")
    post.setRequestProperty("Content-Type", "application/json")
    if (roleId) {
      post.setRequestProperty("X-Vault-Role-Id", (String) roleId)
    }
    post.setRequestProperty(HEADER_BROKER_TOKEN, (String) actionToken)
    def postRC = post.getResponseCode()
    if (!this.isResponseSuccess(postRC)) {
      def errorResponseBody = post.getErrorStream()?.getText() ?: "No error response body"
      def errorMessage = "Failed to provision secret id. Response code: $postRC Response body: $errorResponseBody"
      throw new IllegalStateException(errorMessage)
    }
    def wrappedTokenResponse = jsonSlurper.parseText(post.getInputStream().getText())
    return wrappedTokenResponse.wrap_info.token
  }

  /**
   * Provision a (temporary) token to use with vault.
   * - Positional parameters
   * action       String  The action id from the intention
   * roleId       String  The service role id
   * unwrapToken  Boolean True if the token should be unwrapped for you.
   *
   * Note: The optional unwrapToken must be set false if the caller does
   * use the token directly. Unwrapped token must never be saved to disk or
   * transmitted.
   */
  public String provisionToken(action, roleId = null, unwrapToken = true) {
    if (!action) {
      throw new IllegalArgumentException()
    }
    if (!this.openResponse) {
      throw new IllegalStateException()
    }
    def actionToken = this.openResponse?.actions[action].token
    if (!actionToken) {
      throw new IllegalArgumentException()
    }
    def jsonSlurper = new groovy.json.JsonSlurperClassic()
    def post = new URL(this.BROKER_BASE_URL + "provision/token/self").openConnection()
    post.setRequestMethod("POST")
    post.setRequestProperty("Content-Type", "application/json")
    if (roleId) {
      post.setRequestProperty("X-Vault-Role-Id", (String) roleId)
    }
    post.setRequestProperty(HEADER_BROKER_TOKEN, (String) actionToken)
    def postRC = post.getResponseCode()
    if (!this.isResponseSuccess(postRC)) {
      def errorResponseBody = post.getErrorStream()?.getText() ?: "No error response body"
      def errorMessage = "Failed to provision token. Response code: $postRC Response body: $errorResponseBody"

      throw new IllegalStateException(errorMessage)
    }
    def wrappedTokenResponse = jsonSlurper.parseText(post.getInputStream().getText())

    if (!unwrapToken) {
      return wrappedTokenResponse.wrap_info.token
    }

    return Vault.unwrapToken(wrappedTokenResponse.wrap_info.token)
  }

  private boolean isResponseSuccess(code) {
    return code >= 200 && code <= 299
  }

  private void updatePackageForAction(String actionName, String key, String value) {
    if (value) {
      for (action in this.intention.actions) {
        if (action.action == actionName) {
          action.package[key] = value
        }
      }
    }
  }
}

