package usecases.get_latest_stories;

/**
 * Input Boundary Interface for Get Latest Stories use-case.
 * Implemented by GlsInteractor
 */

public interface GlsGateway {
    GlsGatewayOutputData getAllStories ();
}
