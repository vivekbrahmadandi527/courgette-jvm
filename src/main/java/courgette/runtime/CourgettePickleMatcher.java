package courgette.runtime;

import io.cucumber.core.filter.Filters;
import io.cucumber.core.gherkin.Feature;
import io.cucumber.core.gherkin.Pickle;
import io.cucumber.core.internal.gherkin.pickles.PickleLocation;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CourgettePickleMatcher {
    private final Feature feature;
    private final Filters filters;

    public CourgettePickleMatcher(Feature feature, Filters filters) {
        this.feature = feature;
        this.filters = filters;
    }

    public boolean matches() {
        AtomicBoolean matched = new AtomicBoolean();

        try {
            feature.getPickles().forEach(pickle -> {
                matched.set(filters.test(pickle));
                if (matched.get()) {
                    throw new ConditionSatisfiedException();
                }
            });
        } catch (ConditionSatisfiedException ignored) {
        }
        return matched.get();
    }

    public PickleLocation matchLocation(int pickleLocationLine) {
        final PickleLocation[] location = {null};

        List<Pickle> pickles = feature.getPickles();

        try {
            pickles.stream().filter(p -> p.getLocation().getLine() == pickleLocationLine)
                    .findFirst()
                    .ifPresent(pickleEvent -> {
                        if (filters.test(pickleEvent)) {
                            location[0] = new PickleLocation(pickleEvent.getLocation().getLine(), pickleEvent.getLocation().getColumn());
                            throw new ConditionSatisfiedException();
                        }
                    });
        } catch (ConditionSatisfiedException ignored) {
        }
        return location[0];
    }

    private class ConditionSatisfiedException extends RuntimeException {
    }
}