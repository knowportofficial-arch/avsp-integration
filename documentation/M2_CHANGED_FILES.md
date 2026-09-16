# M2 Changed Files

Complete project after M2 Script AI implementation.

## Minimal M1 compatibility touches

- app/src/main/java/com/avsp/pro/core/module/ModuleStatus.kt
- app/src/main/java/com/avsp/pro/database/DatabaseProvider.kt
- app/src/main/java/com/avsp/pro/repository/ModuleStatusRepositoryImpl.kt
- app/src/main/java/com/avsp/pro/di/AppContainer.kt
- app/src/main/java/com/avsp/pro/ui/navigation/AvspDestination.kt
- app/src/main/java/com/avsp/pro/ui/navigation/AvspNavHost.kt
- app/src/main/java/com/avsp/pro/ui/viewmodel/ViewModelFactory.kt
- app/src/main/java/com/avsp/pro/ui/screens/projects/ProjectDetailScreen.kt
- app/src/main/java/com/avsp/pro/ui/screens/modules/ModulesScreen.kt
- app/src/test/java/com/avsp/pro/M1CoreSuiteTest.kt
- app/src/test/java/com/avsp/pro/core/ContractsTest.kt
- app/src/test/java/com/avsp/pro/core/FrozenModuleStatusTest.kt

## M2 new sources

app/src/main/java/com/avsp/pro/script/contract/ScriptContracts.kt
app/src/main/java/com/avsp/pro/script/generator/DefaultScriptGeneratorRegistry.kt
app/src/main/java/com/avsp/pro/script/generator/MockScriptGenerator.kt
app/src/main/java/com/avsp/pro/script/generator/ScriptGenerator.kt
app/src/main/java/com/avsp/pro/script/integration/ScriptToTtsContract.kt
app/src/main/java/com/avsp/pro/script/language/ScriptLanguage.kt
app/src/main/java/com/avsp/pro/script/repository/ScriptRepository.kt
app/src/main/java/com/avsp/pro/script/ui/ScriptAiScreen.kt
app/src/main/java/com/avsp/pro/script/ui/ScriptAiViewModel.kt
app/src/main/java/com/avsp/pro/script/validation/ScriptValidator.kt

app/src/test/java/com/avsp/pro/script/M2NoSecretsScanTest.kt
app/src/test/java/com/avsp/pro/script/M2ScriptAiTest.kt
