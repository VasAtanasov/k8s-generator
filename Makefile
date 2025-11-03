## k8s-generator — Developer Makefile
# Usage examples:
#   make build                     # Build shaded jar (skip tests)
#   make test                      # Run tests
#   make run ENGINE=kind           # Build + run with defaults (m1/pt)
#   make run MODULE=m7 TYPE=hw ENGINE=minikube OUT=hw-m7
#   make help                      # Show targets

.PHONY: help build package test run run-kind run-minikube run-clean run-unique clean

MVN ?= mvn
MVN_FLAGS ?= -q -Dstyle.color=never
# Silence JDK native access warnings from Jansi during mvn (optional)
MAVEN_OPTS ?= --enable-native-access=ALL-UNNAMED
JAVA ?= java
JAR  ?= target/k8s-generator-1.0.0-SNAPSHOT.jar

# Smart defaults for Phase 1
MODULE ?= m1
TYPE   ?= pt
ENGINE ?= minikube
OUT    ?= $(TYPE)-$(MODULE)
ARGS   ?= --module $(MODULE) --type $(TYPE) $(ENGINE) --out $(OUT)

help:
	@echo "Available targets:"
	@echo "  build         - mvn clean package -DskipTests"
	@echo "  test          - mvn test"
	@echo "  run           - Build + run with ARGS (defaults: m1/pt/$(ENGINE))"
	@echo "                 e.g. make run ENGINE=kind"
	@echo "  run-kind      - Build + run kind with defaults"
	@echo "  run-minikube  - Build + run minikube with defaults"
	@echo "  run-clean     - Remove OUT dir then run (dangerous: deletes $(OUT))"
	@echo "  run-unique    - Run with OUT suffixed by timestamp (no overwrite)"
	@echo "  clean         - mvn clean"
	@echo "Variables: MODULE, TYPE, ENGINE, OUT, ARGS"

build: package

package:
	MAVEN_OPTS="$(MAVEN_OPTS)" $(MVN) $(MVN_FLAGS) clean package -DskipTests

test:
	MAVEN_OPTS="$(MAVEN_OPTS)" $(MVN) $(MVN_FLAGS) test

run: package
	@echo "Running: $(JAR) $(ARGS)"
	$(JAVA) -jar $(JAR) $(ARGS)

run-kind:
	$(MAKE) run ENGINE=kind

run-minikube:
	$(MAKE) run ENGINE=minikube

run-clean: package
	@echo "Removing existing OUT directory: $(OUT)" && rm -rf "$(OUT)" || true
	@echo "Running: $(JAR) $(ARGS)"
	$(JAVA) -jar $(JAR) $(ARGS)

run-unique:
	$(MAKE) run OUT=$(OUT)-$$(date +%Y%m%d%H%M%S)

clean:
	MAVEN_OPTS="$(MAVEN_OPTS)" $(MVN) $(MVN_FLAGS) clean
