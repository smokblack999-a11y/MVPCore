package serg.chuprin.sample.common.di;

import dagger.Module;
import serg.chuprin.sample.repositories.list.di.RepositoriesListComponent;
import serg.chuprin.sample.users.info.di.UserComponent;
import serg.chuprin.sample.users.list.di.UsersListComponent;

/**
 * Explicitly declares the subcomponents owned by AppComponent.
 * This keeps Dagger's component graph deterministic across CI/JDK toolchains.
 */
@Module(subcomponents = {
        UserComponent.class,
        UsersListComponent.class,
        RepositoriesListComponent.class
})
public final class AppModule {
}
