package org.remus.resticexplorer.scanning;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class RetentionPolicyResult {

    private boolean fulfilled;
    private List<String> violations = new ArrayList<>();

    public static RetentionPolicyResult ok() {
        RetentionPolicyResult result = new RetentionPolicyResult();
        result.fulfilled = true;
        return result;
    }
}
