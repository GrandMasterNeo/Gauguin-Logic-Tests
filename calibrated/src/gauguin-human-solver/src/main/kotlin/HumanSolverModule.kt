import org.koin.core.module.Module
import org.koin.core.module.dsl.binds
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import org.piepmeyer.gauguin.creation.GridAcceptanceVerifier
import org.piepmeyer.gauguin.difficulty.human.HumanDifficultyCalculatorFactory
import org.piepmeyer.gauguin.difficulty.human.HumanDifficultyCalculatorFactoryImpl
import org.piepmeyer.gauguin.difficulty.human.HumanGridAcceptanceVerifier

class HumanSolverModule {
    fun module(): Module =
        module {
            single {
                HumanDifficultyCalculatorFactoryImpl()
            } withOptions {
                binds(listOf(HumanDifficultyCalculatorFactory::class))
            }

            single<GridAcceptanceVerifier> { HumanGridAcceptanceVerifier() }
        }
}
