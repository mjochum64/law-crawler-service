# Hive Mind Context Restoration Instructions

## Quick Start Command
```bash
# To resume exactly where this session left off:
cd /home/mjochum/projekte/KI_TESTS/law-crawler-service
cat CLAUDE.md && cat .claude/swarm-context.json
```

## Full Context Restoration Process

### 1. Project Context Loading
```bash
# Read main project documentation
cat CLAUDE.md

# Load detailed swarm session context  
cat .claude/swarm-context.json
```

### 2. MCP Memory Restoration
```bash
# If MCP claude-flow is available, restore memory:
npx claude-flow memory list --namespace hive-collective
npx claude-flow memory retrieve --key "hive/objective" --namespace hive-collective
npx claude-flow memory retrieve --key "workers/researcher-alpha/architecture-summary" --namespace hive-collective
```

### 3. System Validation
```bash
# Validate build and dependencies
mvn clean compile

# Run tests to ensure system integrity
mvn test

# Check application configuration
cat src/main/resources/application.yml
```

### 4. New Hive Mind Session Initialization
If you need to create a new swarm session with full context:

```javascript
// Initialize new hive mind with preserved context
mcp__claude-flow__swarm_init({
  "topology": "hierarchical", 
  "maxAgents": 4,
  "strategy": "specialized"
})

// Restore memory from previous session
mcp__claude-flow__memory_usage({
  "action": "retrieve",
  "namespace": "hive-collective" 
})

// Spawn agents with preserved capabilities
mcp__claude-flow__agent_spawn({
  "type": "researcher",
  "capabilities": ["codebase_analysis", "documentation_analysis", "architecture_discovery"]
})
```

## Context Verification Checklist
- [ ] CLAUDE.md exists and contains project overview
- [ ] .claude/swarm-context.json contains session state
- [ ] MCP memory namespace "hive-collective" accessible  
- [ ] Maven build succeeds (mvn clean compile)
- [ ] Tests pass (mvn test)
- [ ] Application.yml configuration valid

## Emergency Context Recovery
If context files are missing or corrupted:
1. Analyze git history: `git log --oneline -10`
2. Review recent file changes: `git status`
3. Rebuild context from source code analysis
4. Validate with: `mvn spring-boot:run` (should start successfully)

**Last Updated**: 2025-08-07T04:35:00Z  
**Hive Session**: swarm_1754541238628_58dzufkk2