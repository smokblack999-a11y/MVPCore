package serg.chuprin.sample.common.di;

import javax.inject.Singleton;

import dagger.Component;
import serg.chuprin.sample.repositories.list.di.RepositoriesListComponent;
import serg.chuprin.sample.repositories.list.di.RepositoriesListModule;
import serg.chuprin.sample.users.info.di.UserComponent;
import serg.chuprin.sample.users.info.di.UserModule;
import serg.chuprin.sample.users.list.di.UsersListComponent;
import serg.chuprin.sample.users.list.di.UsersListModule;

@Component
@Singleton
public interface AppComponent {

    UserComponent userComponent(UserModule module);

    UsersListComponent usersListComponent(UsersListModule module);

    RepositoriesListComponent repositoriesListComponent(RepositoriesListModule module);

}
