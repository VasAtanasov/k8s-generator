## k8s-generator — Developer Makefile
# Usage examples:
#   make build                                      # Build shaded jar (skip tests)
#   make test                                       # Run all tests
#   make run MODULE=m1 TYPE=pt ENGINE=kind          # Build + run
#   make run-kind MODULE=m7 TYPE=hw                 # Build + run with kind
#   make test-class TEST_CLASS=SemanticValidatorTest
#   make help                                       # Show all targets

.PHONY: help build package test test-class test-class-debug run run-kind run-minikube run-clean run-unique clean

MVN ?= mvnd
MVN_FLAGS ?= -q -Dstyle.color=never
# Silence JDK native access warnings from Jansi during mvn (optional)
# MAVEN_OPTS ?= --enable-native-access=ALL-UNNAMED
JAVA ?= java
JAR  ?= target/k8s-generator-1.0.0-SNAPSHOT.jar

# Test configuration
DEBUG_PORT ?= 5005

# Smart defaults for Phase 1
# MODULE, TYPE, ENGINE are required - no defaults
OUT    ?= $(TYPE)-$(MODULE)
ARGS   ?= --module $(MODULE) --type $(TYPE) $(ENGINE) --out $(OUT)

help:
	@echo "Available targets:"
	@echo "  build              - mvn clean package -DskipTests"
	@echo "  test               - mvn test (all tests)"
	@echo "  test-class         - Run specific test class (requires TEST_CLASS)"
	@echo "  test-class-debug   - Run test class with debugger (requires TEST_CLASS)"
	@echo "  run                - Build + run (requires MODULE, TYPE, ENGINE)"
	@echo "  run-kind           - Build + run with kind (requires MODULE, TYPE)"
	@echo "  run-minikube       - Build + run with minikube (requires MODULE, TYPE)"
	@echo "  run-clean          - Remove OUT dir then run (requires MODULE, TYPE, ENGINE)"
	@echo "  run-unique         - Run with timestamp suffix (requires MODULE, TYPE, ENGINE)"
	@echo "  clean              - mvn clean"
	@echo ""
	@echo "Variables:"
	@echo "  MODULE (required) - Module number (e.g., m1, m7)"
	@echo "  TYPE (required) - Type (e.g., pt, hw, exam-prep)"
	@echo "  ENGINE (required) for run/run-clean/run-unique) - Cluster engine (kind, minikube)"
	@echo "  OUT (optional) - Output directory (default: TYPE-MODULE)"
	@echo "  TEST_CLASS (required for test-class) - Test class name"
	@echo "  DEBUG_PORT (default: $(DEBUG_PORT) - Debug port for test-class-debug"
	@echo ""
	@echo "Examples:"
	@echo "  make run MODULE=m1 TYPE=pt ENGINE=kind"
	@echo "  make run-kind MODULE=m7 TYPE=hw"
	@echo "  make run-minikube MODULE=m1 TYPE=exam-prep OUT=custom-dir"
	@echo "  make test-class TEST_CLASS=SemanticValidatorTest"
	@echo "  make test-class-debug TEST_CLASS=PolicyValidatorTest"

build: package

package:
	MAVEN_OPTS="$(MAVEN_OPTS)" $(MVN) $(MVN_FLAGS) clean package -DskipTests

test:
	MAVEN_OPTS="$(MAVEN_OPTS)" $(MVN) $(MVN_FLAGS) test

test-class:
ifndef TEST_CLASS
	$(error TEST_CLASS is required. Usage: make test-class TEST_CLASS=SemanticValidatorTest)
endif
	@echo "Running test class: $(TEST_CLASS)"
	MAVEN_OPTS="$(MAVEN_OPTS)" $(MVN) test -Dtest=$(TEST_CLASS) -Dsurefire.printSummary=true -DtrimStackTrace=false

test-class-debug:
ifndef TEST_CLASS
	$(error TEST_CLASS is required. Usage: make test-class-debug TEST_CLASS=SemanticValidatorTest)
endif
	@echo "Running test class with debugger: $(TEST_CLASS)"
	@echo "Debugger listening on port $(DEBUG_PORT)"
	@echo "Attach your IDE debugger to localhost:$(DEBUG_PORT)"
	@echo ""
	MAVEN_OPTS="$(MAVEN_OPTS)" $(MVN) test -Dtest=$(TEST_CLASS) -Dmaven.surefire.debug

run: package
ifndef MODULE
	$(error MODULE is required. Usage: make run MODULE=m1 TYPE=pt ENGINE=kind)
endif
ifndef TYPE
	$(error TYPE is required. Usage: make run MODULE=m1 TYPE=pt ENGINE=kind)
endif
ifndef ENGINE
	$(error ENGINE is required. Usage: make run MODULE=m1 TYPE=pt ENGINE=kind)
endif
	@echo "Running: $(JAR) $(ARGS)"
	$(JAVA) -jar $(JAR) $(ARGS)

run-kind:
ifndef MODULE
	$(error MODULE is required. Usage: make run-kind MODULE=m1 TYPE=pt)
endif
ifndef TYPE
	$(error TYPE is required. Usage: make run-kind MODULE=m1 TYPE=pt)
endif
	$(MAKE) run ENGINE=kind MODULE=$(MODULE) TYPE=$(TYPE)

run-minikube:
ifndef MODULE
	$(error MODULE is required. Usage: make run-minikube MODULE=m1 TYPE=pt)
endif
ifndef TYPE
	$(error TYPE is required. Usage: make run-minikube MODULE=m1 TYPE=pt)
endif
	$(MAKE) run ENGINE=minikube MODULE=$(MODULE) TYPE=$(TYPE)

run-clean: package
ifndef MODULE
	$(error MODULE is required. Usage: make run-clean MODULE=m1 TYPE=pt ENGINE=kind)
endif
ifndef TYPE
	$(error TYPE is required. Usage: make run-clean MODULE=m1 TYPE=pt ENGINE=kind)
endif
ifndef ENGINE
	$(error ENGINE is required. Usage: make run-clean MODULE=m1 TYPE=pt ENGINE=kind)
endif
	@echo "Removing existing OUT directory: $(OUT)" && rm -rf "$(OUT)" || true
	@echo "Running: $(JAR) $(ARGS)"
	$(JAVA) -jar $(JAR) $(ARGS)

run-unique:
ifndef MODULE
	$(error MODULE is required. Usage: make run-unique MODULE=m1 TYPE=pt ENGINE=kind)
endif
ifndef TYPE
	$(error TYPE is required. Usage: make run-unique MODULE=m1 TYPE=pt ENGINE=kind)
endif
ifndef ENGINE
	$(error ENGINE is required. Usage: make run-unique MODULE=m1 TYPE=pt ENGINE=kind)
endif
	$(MAKE) run OUT=$(OUT)-$$(date +%Y%m%d%H%M%S) MODULE=$(MODULE) TYPE=$(TYPE) ENGINE=$(ENGINE)

clean:
	MAVEN_OPTS="$(MAVEN_OPTS)" $(MVN) $(MVN_FLAGS) clean
