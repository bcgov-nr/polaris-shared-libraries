package ca.bc.gov.nrids.polaris

class Podman implements Serializable {
  /** Podman agent label for agent with unrestricted web access */
  static final String AGENT_LABEL_WEB = "podman-external"
  /** Podman agent label for agent with restricted web access */
  static final String AGENT_LABEL_APP = "podman"
  /** Podman agent label for agent with no web access */
  static final String AGENT_LABEL_DATA = "podman-data"

  def steps
  def env
  String imagePrefix
  String authfile = ".docker.config.json"

  Podman(steps, env = null, String imagePrefix = "artifacts.developer.gov.bc.ca/docker-remote") {
    this.steps = steps
    this.imagePrefix = imagePrefix
    this.env = env ? env.getEnvironment() : null
  }

  /**
   * Podman build command
   * - Positional parameters
   * context          string  The specified build context directory
   * - Named parameters
   * tag:             string  Tag name to apply to the build image
   * options:         string  Additional options
   * authfile:        string  If set, override the default authfile
   */
  def build(Map args, String context) {
    def shellCmd = (args.httpProxy ? "HTTP_PROXY=${args.httpProxy} " : "") +
      "podman build --authfile=${args.authfile ?: authfile} ${args.options ?: ''} --no-cache ${args.tag ? '--tag ' + args.tag : ''} ${context}";
    steps.sh shellCmd
  }
  def build(Map args) {
    this.build(args, '')
  }
  def build(String context) {
    this.build([:], context)
  }

  /**
   * Podman login command
   * - Named parameters
   * httpProxy:       string  If set, sets HTTP_PROXY for podman command
   * options:         string  Run arguments like '--authfile=/path/to/auth.json'
   * authfile:        string  If set, override the default authfile
   *
   * - Registry credentials
   * For login purposes, store registry credentials in Vault and use the Vault class to
   * retrieve them. For example:
   *
   * def vault = new Vault(vaultToken)
   * def registryCreds = vault.read('apps/data/prod/path/to/registry/credentials')
   * env.REGISTRY_USERNAME = registryCreds['username']
   * env.REGISTRY_PASSWORD = registryCreds['password']
   *
   * Note: The Vault path must include "data" after the secrets engine mount point.
   *
   */
  def login(Map args) {
    if (args.authfile) {
      authfile = args.authfile
    }
    def shellCmd = (args.httpProxy ? "HTTP_PROXY=${args.httpProxy} " : "") +
      "set +x ; podman login --authfile=${authfile} ${args.options ?: ''} ${args.registry ?: imagePrefix}";
    steps.sh shellCmd
  }

  /**
   * Podman logout command
   * - Named parameters
   * httpProxy:       string  If set, sets HTTP_PROXY for podman command
   * options:         string  Run arguments like '--authfile=/path/to/auth.json'
   * authfile:        string  If set, override the default authfile
   */
  def logout(Map args) {
    def shellCmd = (args.httpProxy ? "HTTP_PROXY=${args.httpProxy} " : "") +
      "podman logout --authfile=${args.authfile ?: authfile} ${args.options ?: ''} --all";
    steps.sh shellCmd
  }

  /**
   * Podman pull command
   * - Positional parameters
   * imageId          string  The name of the container image to run. Combined with image prefix if set in constructor
   * - Named parameters
   * options:         string  Run arguments like '-v \$(pwd)/fb/config:/app/config'
   * httpProxy:       string  If set, sets HTTP_PROXY for podman command
   * imagePrefix:     string  If set, override image prefix set in constructor
   * authfile:        string  If set, override the default authfile
   */
  def pull(Map args, String imageId) {
    def shellCmd = (args.httpProxy ? "HTTP_PROXY=${args.httpProxy} " : "") +
      "podman pull --authfile=${args.authfile ?: authfile} ${args.options ?: ''} ${renderImageId(args, imageId)}";
    steps.sh shellCmd
  }
  def pull(String imageId) {
    this.pull([:], imageId)
  }

  /**
   * Podman push command
   * - Positional parameters
   * imageId          string  The name of the container image to run. Combined with image prefix if set in constructor
   * - Named parameters
   * httpProxy:       string  If set, sets HTTP_PROXY for podman command
   * imagePrefix:     string  If set, override image prefix set in constructor
   * username         string  The username to use to authenticate with the registry
   * password         string  The password to use to authenticate with the registry
   * authfile:        string  If set, override the default authfile
   */
  def push(Map args, String imageId) {
    def shellCmd = (args.httpProxy ? "HTTP_PROXY=${args.httpProxy} " : "") +
      "podman push --authfile=${args.authfile ?: authfile} ${imageId} ${renderImageId(args, imageId)}";
    steps.sh shellCmd
  }

  /**
   * Podman run command
   * - Positional parameters
   * imageId          string  The name of the container image to run. Combined with image prefix if set in constructor
   * - Named parameters
   * command:         string  Overrive contianer command (and any options).
   * options:         string  Run arguments like '-v \$(pwd)/fb/config:/app/config'
   * httpProxy:       string  If set, sets HTTP_PROXY for podman command
   * imagePrefix:     string  If set, override image prefix set in constructor
   * returnStdout:    bool    If true, return standard out
   * skipPipelineEnv: bool    If true (or if env in constructor was null), do not automatically add all environment variables
   * authfile:        string  If set, override the default authfile
   */
  def run(Map args, String imageId) {
    def toEnvOptions = {
      it.collect { "-e ${it.key}=${it.value.replaceAll(' ', '\\\\ ')}" } join " "
    }
    def envOpts = this.env && !args.skipPipelineEnv ? toEnvOptions(this.env) : ''
    def shellCmd = (args.httpProxy ? "HTTP_PROXY=${args.httpProxy} " : "") +
      "podman run --rm " +
      (args.authfile ?  "--authfile=${args.authfile} " : "") +
      "${args.options ?: ''} ${envOpts} ${renderImageId(args, imageId)} ${args.command ?: ''}"
    if (args.returnStdout)
      return steps.sh(script: shellCmd, returnStdout: true)
    else {
      steps.sh shellCmd
    }
  }
  def run(String imageId) {
    this.run([:], imageId)
  }

  /**
   * Podman version command
   */
  def version(Map args) {
    steps.sh "podman version"
  }
  def version() {
    this.version([:])
  }

  /**
   * Private: Renders image prefix
   */
  def renderImageId(Map args, String imageId) {
    def prefix = !args.containsKey('imagePrefix') || args.imagePrefix == null ? imagePrefix : args.imagePrefix
    return (prefix ? prefix + '/' : '') + imageId
  }
}
